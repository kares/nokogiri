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

package nokogiri;

import static nokogiri.internals.NokogiriHelpers.getNokogiriClass;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyFixnum;
import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.w3c.dom.Node;

/**
 * DTD entity declaration.
 *
 * @author Patrick Mahoney <pat@polycrystal.org>
 * @author Yoko Harada <yokolet@gmail.com>
 */
@JRubyClass(name="Nokogiri::XML::EntityDecl", parent="Nokogiri::XML::Node")
public class XmlEntityDecl extends XmlNode {
    public static final int INTERNAL_GENERAL = 1;
    public static final int EXTERNAL_GENERAL_PARSED = 2;
    public static final int EXTERNAL_GENERAL_UNPARSED  = 3;
    public static final int INTERNAL_PARAMETER = 4;
    public static final int EXTERNAL_PARAMETER = 5;
    public static final int INTERNAL_PREDEFINED = 6;
    
    private IRubyObject entityType;
    private IRubyObject name;
    private IRubyObject external_id;
    private IRubyObject system_id;
    private IRubyObject content;

    XmlEntityDecl(Ruby runtime, RubyClass klass) {
        super(runtime, klass);
    }

    /**
     * Initialize based on an entityDecl node from a NekoDTD parsed DTD.
     */
    public XmlEntityDecl(Ruby runtime, RubyClass klass, Node entDeclNode) {
        super(runtime, klass, entDeclNode);
        entityType = RubyFixnum.newFixnum(runtime, XmlEntityDecl.INTERNAL_GENERAL);
        name = external_id = system_id = content = runtime.getNil();
    }
    
    public XmlEntityDecl(Ruby runtime, RubyClass klass, Node entDeclNode, IRubyObject[] argv) {
        super(runtime, klass, entDeclNode);
        name = argv[0];
        entityType = RubyFixnum.newFixnum(runtime, XmlEntityDecl.INTERNAL_GENERAL);
        external_id = system_id = content = runtime.getNil();

        if (argv.length > 1) entityType = argv[1];
        if (argv.length > 4) {
            external_id = argv[2];
            system_id = argv[3];
            content = argv[4];
        }
    }

    static XmlEntityDecl create(ThreadContext context, Node entDeclNode) {
        return new XmlEntityDecl(context.runtime,
            getNokogiriClass(context.runtime, "Nokogiri::XML::EntityDecl"),
            entDeclNode
        );
    }
    
    // when entity is created by create_entity method
    static XmlEntityDecl create(ThreadContext context, Node entDeclNode, IRubyObject... argv) {
        return new XmlEntityDecl(context.runtime,
            getNokogiriClass(context.runtime, "Nokogiri::XML::EntityDecl"),
            entDeclNode, argv
        );
    }

    /**
     * Returns the local part of the element name.
     */
    @Override
    @JRubyMethod
    public IRubyObject node_name(ThreadContext context) {
        IRubyObject value = getAttribute(context, "name");
        if (value == context.nil) value = name;
        return value;
    }

    @Override
    @JRubyMethod(name = "node_name=")
    public IRubyObject node_name_set(ThreadContext context, IRubyObject name) {
        throw context.runtime.newRuntimeError("cannot change name of DTD decl");
    }

    @JRubyMethod
    public IRubyObject content(ThreadContext context) {
        IRubyObject value = getAttribute(context, "value");
        if (value == context.nil) value = content;
        return value;
    }

    // TODO: what is content vs. original_content?
    @JRubyMethod
    public IRubyObject original_content(ThreadContext context) {
        return getAttribute(context, "value");
    }

    @JRubyMethod
    public IRubyObject system_id(ThreadContext context) {
        IRubyObject value = getAttribute(context, "sysid");
        if (value == context.nil) value = system_id;
        return value;
    }

    @JRubyMethod
    public IRubyObject external_id(ThreadContext context) {
        IRubyObject value = getAttribute(context, "pubid");
        if (value == context.nil) value = external_id;
        return value;
    }

    @JRubyMethod
    public IRubyObject entity_type(ThreadContext context) {
        return entityType;
    }
}
