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

import nokogiri.internals.HtmlDomParserContext;
import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;
import org.jruby.javasupport.util.RuntimeHelpers;
import org.jruby.runtime.Arity;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static nokogiri.internals.NokogiriHelpers.getNokogiriClass;

/**
 * Class for Nokogiri::HTML::Document.
 *
 * @author sergio
 * @author Yoko Harada <yokolet@gmail.com>
 */
@JRubyClass(name="Nokogiri::HTML::Document", parent="Nokogiri::XML::Document")
public class HtmlDocument extends XmlDocument {
    private static final String DEFAULT_CONTENT_TYPE = "html";
    private static final String DEFAULT_PUBLIC_ID = "-//W3C//DTD HTML 4.01//EN";
    private static final String DEFAULT_SYTEM_ID = "http://www.w3.org/TR/html4/strict.dtd";

    private String parsed_encoding = null;

    public HtmlDocument(Ruby runtime, RubyClass klazz) {
        super(runtime, klazz);
    }

    public HtmlDocument(Ruby runtime, Document doc) {
        this(runtime, getNokogiriClass(runtime, "Nokogiri::HTML::Document"), doc);
    }

    HtmlDocument(Ruby runtime, RubyClass klazz, Document doc) {
        super(runtime, klazz, doc);
    }

    @JRubyMethod(name="new", meta = true, rest = true)
    public static HtmlDocument rbNew(ThreadContext context, IRubyObject klazz, IRubyObject... args) {
        HtmlDocument document;
        try {
            document = new HtmlDocument(context.runtime, (RubyClass) klazz);
        } catch (Exception ex) {
            throw context.runtime.newRuntimeError("couldn't create document: " + ex);
        }

        RuntimeHelpers.invoke(context, document, "initialize", args);
        return document;
    }

    public IRubyObject getInternalSubset(ThreadContext context) {
        IRubyObject internalSubset = super.getInternalSubset(context);

        // html documents are expected to have a default internal subset
        // the default values are the same ones used when the following
        // feature is turned on
        // "http://cyberneko.org/html/features/insert-doctype"
        // the reason we don't turn it on, is because it overrides the document's
        // declared doctype declaration.

        if (internalSubset.isNil()) {
            internalSubset = XmlDtd.newEmpty(context.getRuntime(),
                                             getDocument(),
                                             context.getRuntime().newString(DEFAULT_CONTENT_TYPE),
                                             context.getRuntime().newString(DEFAULT_PUBLIC_ID),
                                             context.getRuntime().newString(DEFAULT_SYTEM_ID));
            setInternalSubset(internalSubset);
        }

        return internalSubset;
    }

    public static IRubyObject do_parse(ThreadContext context, RubyClass klass, IRubyObject[] args) {
        Ruby runtime = context.runtime;
        Arity.checkArgumentCount(runtime, args, 4, 4);
        HtmlDomParserContext ctx = new HtmlDomParserContext(runtime, args[2], args[3]);
        ctx.setInputSource(context, args[0], args[1]);
        return ctx.parse(context, klass, args[1]);
    }
    
    public void setDocumentNode(ThreadContext context, Node node) {
        super.setNode(context, node);
        if (node != null) {
            Document document = (Document)node;
            document.normalize();
            stabilzeAttrValue(document.getDocumentElement());
        }
        setInstanceVariable("@decorators", context.nil);
    }
    
    private void stabilzeAttrValue(Node node) {
        if (node == null) return;
        if (node.hasAttributes()) {
            NamedNodeMap nodeMap = node.getAttributes();
            for (int i=0; i<nodeMap.getLength(); i++) {
                Node n = nodeMap.item(i);
                if (n instanceof Attr) {
                    Attr attr = (Attr)n;
                    String attrName = attr.getName();
                    // not sure, but need to get value always before document is referred.
                    // or lose attribute value
                    String attrValue = attr.getValue(); // don't delete this line
                }
            }
        }
        NodeList children = node.getChildNodes();
        for (int i=0; i<children.getLength(); i++) {
            stabilzeAttrValue(children.item(i));
        }
    }
    
    public void setParsedEncoding(String encoding) {
        parsed_encoding = encoding;
    }
    
    public String getPraedEncoding() {
        return parsed_encoding;
    }

    /*
     * call-seq:
     *  read_io(io, url, encoding, options)
     *
     * Read the HTML document from +io+ with given +url+, +encoding+,
     * and +options+.  See Nokogiri::HTML.parse
     */
    @JRubyMethod(meta = true, rest = true)
    public static IRubyObject read_io(ThreadContext context, IRubyObject klass, IRubyObject[] args) {
        return do_parse(context, (RubyClass) klass, args);
    }

    /*
     * call-seq:
     *  read_memory(string, url, encoding, options)
     *
     * Read the HTML document contained in +string+ with given +url+, +encoding+,
     * and +options+.  See Nokogiri::HTML.parse
     */
    @JRubyMethod(meta = true, rest = true)
    public static IRubyObject read_memory(ThreadContext context, IRubyObject klass, IRubyObject[] args) {
        return do_parse(context, (RubyClass) klass, args);
    }

}
