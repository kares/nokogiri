/**
 * (The MIT License)
 *
 * Copyright (c) 2008 - 2012:
 *
 * * {Aaron Patterson}[http://tenderlovemaking.com]
 * * {Mike Dalessio}[http://mike.daless.io]
 * * {Charles Nutter}[http://blog.headius.com]
 * * {Sergio Arbeo}[http://www.serabe.com]
 * * {Patrick Mahoney}[http://polycrystal.org]
 * * {Yoko Harada}[http://yokolet.blogspot.com]
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * 'Software'), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED 'AS IS', WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package nokogiri;

import static nokogiri.internals.NokogiriHelpers.getNokogiriClass;
import static org.jruby.javasupport.util.RuntimeHelpers.invoke;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;

import nokogiri.internals.*;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyException;
import org.jruby.RubyObject;
import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;
import org.jruby.exceptions.RaiseException;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.xml.sax.SAXException;

/**
 * Class for Nokogiri::XML::SAX::PushParser
 *
 * @author Patrick Mahoney <pat@polycrystal.org>
 * @author Yoko Harada <yokolet@gmail.com>
 */
@JRubyClass(name="Nokogiri::XML::SAX::PushParser")
public class XmlSaxPushParser extends RubyObject {
    ParserContext.Options options;
    IRubyObject optionsRuby;
    IRubyObject saxParser;

    NokogiriBlockingQueueInputStream stream;

    private ParserTask parserTask = null;
    private FutureTask<XmlSaxParserContext> futureTask = null;
    private ExecutorService executor = null;

    public XmlSaxPushParser(Ruby ruby, RubyClass rubyClass) {
        super(ruby, rubyClass);
    }

    @Override
    public void finalize() {
        try {
            terminateImpl();
        }
        catch (Exception e) { /* ignored */ }
    }

    @JRubyMethod
    public IRubyObject initialize_native(final ThreadContext context,
                                         IRubyObject saxParser,
                                         IRubyObject fileName) {
        optionsRuby
            = invoke(context, context.getRuntime().getClassFromPath("Nokogiri::XML::ParseOptions"), "new");
        options = new ParserContext.Options(0);
        this.saxParser = saxParser;
        return this;
    }

    /**
     * Returns an integer.
     */
    @JRubyMethod(name="options")
    public IRubyObject getOptions(ThreadContext context) {
        return invoke(context, optionsRuby, "options");
    }

    /**
     * <code>val</code> is an integer.
     */
    @JRubyMethod(name="options=")
    public IRubyObject setOptions(ThreadContext context, IRubyObject val) {
        invoke(context, optionsRuby, "options=", val);
        options =
            new ParserContext.Options(val.convertToInteger().getLongValue());
        return getOptions(context);
    }

    /**
     * Can take a boolean assignment.
     *
     * @param context
     * @param value
     * @return
     */
    @JRubyMethod(name = "replace_entities=")
    public IRubyObject setReplaceEntities(ThreadContext context, IRubyObject value) {
        // Ignore the value.
        return this;
    }

    @JRubyMethod(name="replace_entities")
    public IRubyObject getReplaceEntities(ThreadContext context) {
        // The java parser always replaces entities.
        return context.getRuntime().getTrue();
    }

    @JRubyMethod
    public IRubyObject native_write(ThreadContext context, IRubyObject chunk,
                                    IRubyObject isLast) {
        try {
            initialize_task(context);
        } catch (IOException e) {
            throw context.runtime.newRuntimeError(e.getMessage());
        }
        final ByteArrayInputStream data = NokogiriHelpers.stringBytesToStream(chunk);
        if (data == null) {
            terminateTask(context.runtime);
            throw new RaiseException(XmlSyntaxError.createXMLSyntaxError(context.runtime)); // Nokogiri::XML::SyntaxError
        }

        int errorCount0 = parserTask.getErrorCount();

        if (isLast.isTrue()) {
            try {
                parserTask.getNokogiriHandler().endDocument();
            }
            catch (SAXException e) {
                throw context.runtime.newRuntimeError(e.getMessage());
            }
            terminateTask(context.runtime);
        } else {
            try {
                Future<Void> task = stream.addChunk(data);
                task.get();
            }
            catch (ClosedStreamException ex) {
                // this means the stream is closed, ignore this exception
            }
            catch (Exception e) {
                throw context.runtime.newRuntimeError(e.toString());
            }

        }

        if (!options.recover && parserTask.getErrorCount() > errorCount0) {
            terminateTask(context.runtime);
            throw new RaiseException(parserTask.getLastError(), true);
        }

        return this;
    }

    private void initialize_task(ThreadContext context) throws IOException {
        if (futureTask == null || stream == null) {
            stream = new NokogiriBlockingQueueInputStream();

            assert saxParser != null : "saxParser null";
            parserTask = new ParserTask(context, saxParser, stream);
            futureTask = new FutureTask<XmlSaxParserContext>(parserTask);
            executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
              @Override
              public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("XmlSaxPushParser");
                t.setDaemon(true);
                return t;
              }
            });
            executor.submit(futureTask);
        }
    }

    private void terminateTask(final Ruby runtime) {
        if (executor == null) return;

        try {
            terminateImpl();
        }
        catch (InterruptedException e) {
            throw runtime.newRuntimeError(e.toString());
        }
        catch (Exception e) {
            throw runtime.newRuntimeError(e.toString());
        }
    }

    private synchronized void terminateImpl() throws InterruptedException, ExecutionException {
        terminateExecution(executor, stream, futureTask);

        executor = null; stream = null; futureTask = null;
    }

    // SHARED for HtmlSaxPushParser
    static void terminateExecution(final ExecutorService executor, final NokogiriBlockingQueueInputStream stream,
                                   final FutureTask<?> futureTask)
        throws InterruptedException, ExecutionException {

        if (executor == null) return;

        try {
            Future<Void> task = stream.addChunk(NokogiriBlockingQueueInputStream.END);
            task.get();
        }
        catch (ClosedStreamException ex) {
            // ignore this exception, it means the stream was closed
        }
        futureTask.cancel(true);
        executor.shutdown();
    }

    private static XmlSaxParserContext parse(final Ruby runtime, final InputStream stream) {
        RubyClass klazz = getNokogiriClass(runtime, "Nokogiri::XML::SAX::ParserContext");
        return XmlSaxParserContext.parse_stream(runtime, klazz, stream);
    }

    static class ParserTask extends ParserContext.ParserTask<XmlSaxParserContext> {

        final InputStream stream;

        private ParserTask(ThreadContext context, IRubyObject handler, InputStream stream) {
            this(context, handler, parse(context.runtime, stream), stream);
        }

        // IMPL with HtmlSaxPushParser
        protected ParserTask(ThreadContext context, IRubyObject handler, XmlSaxParserContext parser, InputStream stream) {
            super(context, handler, parser);
            this.stream = stream;
        }

        @Override
        public XmlSaxParserContext call() throws Exception {
            try {
                parser.parse_with(context, handler);
            }
            finally { stream.close(); }
            // we have to close the stream before exiting, otherwise someone
            // can add a chunk and block on task.get() forever.
            return parser;
        }

        final NokogiriHandler getNokogiriHandler() {
            return parser.getNokogiriHandler();
        }

        synchronized final int getErrorCount() {
            // check for null because thread may not have started yet
            if (parser.getNokogiriHandler() == null) return 0;
            return parser.getNokogiriHandler().getErrorCount();
        }

        synchronized final RubyException getLastError() {
            return (RubyException) parser.getNokogiriHandler().getLastError();
        }

    }

}
