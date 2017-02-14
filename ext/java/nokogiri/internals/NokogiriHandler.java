/**
 * (The MIT License)
 *
 * Copyright (c) 2008 - 2011:
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

package nokogiri.internals;

import static nokogiri.internals.NokogiriHelpers.getLocalPart;
import static nokogiri.internals.NokogiriHelpers.getPrefix;
import static nokogiri.internals.NokogiriHelpers.isNamespace;
import static nokogiri.internals.NokogiriHelpers.stringOrNil;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.Stack;

import nokogiri.XmlSyntaxError;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyClass;
import org.jruby.javasupport.util.RuntimeHelpers;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.ext.DefaultHandler2;

/**
 * A handler for SAX parsing.
 * 
 * @author sergio
 * @author Yoko Harada <yokolet@gmail.com>
 */
public class NokogiriHandler extends DefaultHandler2 implements XmlDeclHandler {
    Stack<StringBuffer> characterStack;
    private final Ruby runtime;
    private final RubyClass attrClass;
    private final IRubyObject object;

    /**
     * Stores parse errors with the most-recent error last.
     *
     * TODO: should these be stored in the document 'errors' array?
     * Currently only string messages are stored there.
     */
    private final LinkedList<XmlSyntaxError> errors = new LinkedList<XmlSyntaxError>();

    private Locator locator;
    private boolean needEmptyAttrCheck;

    public NokogiriHandler(Ruby runtime, IRubyObject object) {
        this.runtime = runtime;
        this.attrClass = (RubyClass) runtime.getClassFromPath("Nokogiri::XML::SAX::Parser::Attribute");
        this.object = object;
        String objectName = object.getMetaClass().getName();
        if ("Nokogiri::HTML::SAX::Parser".equals(objectName)) needEmptyAttrCheck = true;
    }

    @Override
    public void skippedEntity(String skippedEntity) {
        call("error", runtime.newString("Entity '" + skippedEntity + "' not defined\n"));
    }

    @Override
    public void setDocumentLocator(Locator locator) {
        this.locator = locator;
    }

    @Override
    public void startDocument() throws SAXException {
        call("start_document");
        characterStack = new Stack();
    }

    @Override
    public void xmlDecl(String version, String encoding, String standalone) {
        call("xmldecl", stringOrNil(runtime, version),
             stringOrNil(runtime, encoding),
             stringOrNil(runtime, standalone));
    }

    @Override
    public void endDocument() throws SAXException {
        StringBuffer sb;
        if (!characterStack.empty()) {
            for (int i=0; i<characterStack.size(); i++) {
                sb = characterStack.get(i);
                call("characters", runtime.newString(sb.toString()));
            }
        }
        call("end_document");
    }

    @Override
    public void processingInstruction(String target, String data) {
      call("processing_instruction", runtime.newString(target), runtime.newString(data));
    }

    /*
     * This calls "start_element_namespace".
     *
     * Attributes that define namespaces are passed in a separate
     * array of <code>[:prefix, :uri]</code> arrays and are not
     * passed with the other attributes.
     */
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attrs) throws SAXException {
        final Ruby runtime = this.runtime;
        final ThreadContext context = runtime.getCurrentContext();

        // for attributes other than namespace attrs
        RubyArray rubyAttr = RubyArray.newArray(runtime);
        // for namespace defining attributes
        RubyArray rubyNSAttr = RubyArray.newArray(runtime);

        boolean fromFragmentHandler = false; // isFromFragmentHandler();

        for (int i = 0; i < attrs.getLength(); i++) {
            String u = attrs.getURI(i);
            String qn = attrs.getQName(i);
            String ln = attrs.getLocalName(i);
            String val = attrs.getValue(i);
            String pre;

            pre = getPrefix(qn);
            if (ln == null || ln.isEmpty()) ln = getLocalPart(qn);

            if (isNamespace(qn) && !fromFragmentHandler) {
                // I haven't figured the reason out yet, but, in somewhere,
                // namespace is converted to array in array and cause
                // TypeError at line 45 in fragment_handler.rb
                if (ln.equals("xmlns")) ln = null;
                rubyNSAttr.append( runtime.newArray( stringOrNil(runtime, ln), runtime.newString(val) ) );
            } else {
                IRubyObject[] args = null;
                if (needEmptyAttrCheck) {
                    if (isEmptyAttr(ln)) {
                        args = new IRubyObject[3];
                        args[0] = stringOrNil(runtime, ln);
                        args[1] = stringOrNil(runtime, pre);
                        args[2] = stringOrNil(runtime, u);
                    }
                } 
                if (args == null) {
                    args = new IRubyObject[4];
                    args[0] = stringOrNil(runtime, ln);
                    args[1] = stringOrNil(runtime, pre);
                    args[2] = stringOrNil(runtime, u);
                    args[3] = stringOrNil(runtime, val);
                }

                rubyAttr.append( RuntimeHelpers.invoke(context, attrClass, "new", args) );
            }
        }

        if (localName == null || localName.isEmpty()) localName = getLocalPart(qName);
        call("start_element_namespace",
             stringOrNil(runtime, localName),
             rubyAttr,
             stringOrNil(runtime, getPrefix(qName)),
             stringOrNil(runtime, uri),
             rubyNSAttr);
        characterStack.push(new StringBuffer());
    }

    static final Set<String> EMPTY_ATTRS;
    static {
        final String[] emptyAttrs = {
            "checked", "compact", "declare", "defer", "disabled", "ismap", "multiple",
            "noresize", "nohref", "noshade", "nowrap", "readonly", "selected"
        };
        EMPTY_ATTRS = new HashSet<String>(Arrays.asList(emptyAttrs));
    }
    
    private static boolean isEmptyAttr(String name) {
        return EMPTY_ATTRS.contains(name);
    }
    
    public final Integer getLine() { // -1 if none is available
        final int line = locator.getLineNumber();
        return line == -1 ? null : line;
    }
    
    public final Integer getColumn() { // -1 if none is available
        final int column = locator.getColumnNumber();
        return column == -1 ? null : column - 1;
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        StringBuffer sb = characterStack.pop();
        call("characters", runtime.newString(sb.toString()));
        call("end_element_namespace",
             stringOrNil(runtime, localName),
             stringOrNil(runtime, getPrefix(qName)),
             stringOrNil(runtime, uri));
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        StringBuffer sb = characterStack.peek();
        sb.append(ch, start, length);
    }

    @Override
    public void comment(char[] ch, int start, int length) throws SAXException {
        call("comment", runtime.newString(new String(ch, start, length)));
    }

    @Override
    public void startCDATA() throws SAXException {
        characterStack.push(new StringBuffer());
    }

    @Override
    public void endCDATA() throws SAXException {
        StringBuffer sb = characterStack.pop();
        call("cdata_block", runtime.newString(sb.toString()));
    }

    @Override
    public void error(SAXParseException ex) {
        addError( XmlSyntaxError.createError(runtime, ex) );
        final String msg = ex.getMessage();
        call("error", runtime.newString(msg == null ? "" : msg));
    }

    @Override
    public void fatalError(SAXParseException ex) throws SAXException {
        addError( XmlSyntaxError.createFatalError(runtime, ex) );
        final String msg = ex.getMessage();
        call("error", runtime.newString(msg == null ? "" : msg));
    }

    @Override
    public void warning(SAXParseException ex) {
        final String msg = ex.getMessage();
        call("warning", runtime.newString(msg == null ? "" : msg));
    }

    protected synchronized void addError(XmlSyntaxError e) {
        errors.add(e);
    }

    public synchronized int getErrorCount() {
        return errors.size();
    }

    public synchronized IRubyObject getLastError() {
        return errors.getLast();
    }

    private void call(String methodName) {
        ThreadContext context = runtime.getCurrentContext();
        RuntimeHelpers.invoke(context, document(context.runtime), methodName);
    }

    private void call(String methodName, IRubyObject argument) {
        ThreadContext context = runtime.getCurrentContext();
        RuntimeHelpers.invoke(context, document(context.runtime), methodName, argument);
    }

    private void call(String methodName, IRubyObject arg1, IRubyObject arg2) {
        ThreadContext context = runtime.getCurrentContext();
        RuntimeHelpers.invoke(context, document(context.runtime), methodName, arg1, arg2);
    }

    private void call(String methodName, IRubyObject arg1, IRubyObject arg2, IRubyObject arg3) {
        ThreadContext context = runtime.getCurrentContext();
        RuntimeHelpers.invoke(context, document(runtime), methodName, arg1, arg2, arg3);
    }

    private void call(String methodName, IRubyObject... args) {
        ThreadContext context = runtime.getCurrentContext();
        RuntimeHelpers.invoke(context, document(runtime), methodName, args);
    }

    private IRubyObject document(final Ruby runtime) {
        if (object != null) {
            return object.getInstanceVariables().getInstanceVariable("@document");
        }
        return runtime.getNil();
    }

}
