/*
 * {{{ header & license
 * Copyright (c) 2004 Joshua Marinacci, Torbj�rn Gannholm
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * }}}
 */
package org.xhtmlrenderer.render;

import org.xhtmlrenderer.css.newmatch.CascadedStyle;
import org.xhtmlrenderer.layout.Context;
import org.xhtmlrenderer.layout.block.Relative;
import org.xhtmlrenderer.layout.content.*;
import org.xhtmlrenderer.util.GraphicsUtil;

import java.awt.*;
import java.awt.font.LineMetrics;
import java.util.Iterator;

public class InlineRendering {

    /**
     * Description of the Method
     *
     * @param c   PARAM
     * @param box PARAM
     */
    public static void paintComponent(Context c, Box box) {
        if (box.getBlockFormattingContext() != null) c.pushBFC(box.getBlockFormattingContext());
        paintInlineContext(c, box);
        if (box.getBlockFormattingContext() != null) c.popBFC();
    }

    /**
     * Paint all of the inlines in this box. It recurses through
     * each line, and then each inline in each line, and paints them
     * individually.
     */
    static void paintInlineContext(Context c, Box box) {
        //BlockBox block = (BlockBox)box;
        // translate into local coords
        // account for the origin of the containing box
        c.translate(box.x, box.y);
        // for each line box
        BlockBox block = null;
        if (box instanceof BlockBox) {//Why isn't it always a BlockBox?
            block = (BlockBox) box;
        }
        //if (box.restyle) {
        BoxRendering.restyle(c, box);
        //box.restyle = false;
        //}

        for (int i = 0; i < box.getChildCount(); i++) {
            if (i == 0 && block != null && block.firstLineStyle != null) c.pushStyle(block.firstLineStyle);
            // get the line box
            paintLine(c, (LineBox) box.getChild(i));
            if (i == 0 && block != null && block.firstLineStyle != null) c.popStyle();
        }

        // translate back to parent coords
        c.translate(-box.x, -box.y);
    }

    /**
     * paint all of the inlines on the specified line
     */
    static void paintLine(Context c, LineBox line) {
        // get Xx and y
        int lx = line.x;
        int ly = line.y + line.baseline;

        BoxRendering.restyle(c, line);
        // for each inline box
        for (int j = 0; j < line.getChildCount(); j++) {
            Box child = line.getChild(j);
            if (child.content instanceof AbsolutelyPositionedContent) {
                paintAbsolute(c, child);
                //debugInlines(c, child, lx, ly);
                continue;
            }

            InlineBox box = (InlineBox) child;
            paintInline(c, box, lx, ly, line);
        }
        if (c.debugDrawLineBoxes()) {
            GraphicsUtil.drawBox(c.getGraphics(), line, Color.blue);
        }
    }


    // Inlines are drawn vertically relative to the baseline of the containing
    // line box, not relative to the origin of the line.
    // They *are* drawn horizontally (Xx) relative to the origin of the
    // containing line box though

    static void paintInline(Context c, InlineBox inline, int lx, int ly, LineBox line) {
        // Uu.p("paintInline: " + inline);
        if (inline.content instanceof InlineBlockContent) {
            paintReplaced(c, inline, line);
            debugInlines(c, inline, lx, ly);
            return;
        }

        if (inline.content instanceof FloatedBlockContent) {
            paintFloat(c, inline);
            debugInlines(c, inline, lx, ly);
            return;
        }

        if (inline.pushstyles != null) {
            for (Iterator i = inline.pushstyles.iterator(); i.hasNext();) {
                StylePush sp = (StylePush) i.next();
                c.pushStyle(c.getCss().getCascadedStyle(sp.getElement()));
                if (inline.hover) {
                    CascadedStyle hs = c.getCss().getPseudoElementStyle(sp.getElement(), "hover");
                    if (hs != null) c.pushStyle(hs);
                }
                //Now we know that an inline element started here, handle borders and such?
                Relative.translateRelative(c);
                //TODO: push to current border-list (and paint left edge)
                //HACK: this might do for now - tobe 2004-12-27
                paintPadding(c, line, inline);
            }
        }
        //if (box.restyle) {
        BoxRendering.restyle(c, inline);
        //box.restyle = false;
        //}

        //handleRelativePre(c, inline);
        //paintPadding(c, line, inline);
        c.updateSelection(inline);
        
        // calculate the Xx and y relative to the baseline of the line (ly) and the
        // left edge of the line (lx)
        int iy = ly + inline.y;
        int ix = lx + inline.x;
        // account for padding
        // Uu.p("adjusted inline by: " + inline.totalLeftPadding());
        // Uu.p("inline = " + inline);
        // Uu.p("padding = " + inline.padding);
        ix += inline.totalLeftPadding(c.getCurrentStyle());

        paintSelection(c, inline, lx, ly);
        paintText(c, lx, ly, ix, iy, inline);
        debugInlines(c, inline, lx, ly);
        //handleRelativePost(c, inline);

        if (inline.popstyles != null) {
            for (Iterator i = inline.popstyles.iterator(); i.hasNext();) {
                StylePop sp = (StylePop) i.next();
                //TODO: paint right edge and pop current border-list
                Relative.untranslateRelative(c);
                if (inline.hover) {
                    CascadedStyle hs = c.getCss().getPseudoElementStyle(sp.getElement(), "hover");
                    if (hs != null) c.popStyle();
                }
                c.popStyle();
            }
        }
    }


    /*private void handleRelativePre(Context c, InlineBox inline) {
        if (inline.relative) {
            c.translate(inline.left, inline.top);
        }
    }


    private void handleRelativePost(Context c, InlineBox inline) {
        if (inline.relative) {
            c.translate(-inline.left, -inline.top);
        }
    }*/


    static void debugInlines(Context c, InlineBox inline, int lx, int ly) {
        if (c.debugDrawInlineBoxes()) {
            GraphicsUtil.draw(c.getGraphics(), new Rectangle(lx + inline.x + 1, ly + inline.y + 1 - inline.height,
                    inline.width - 2, inline.height - 2), Color.green);
        }
    }


    static void paintReplaced(Context c, InlineBox inline, LineBox line) {
        // Uu.p("paint replaced: " + inline);
        c.translate(line.x, line.y + (line.baseline - inline.height));
        /*Renderer rend = c.getRenderer(inline.content.getElement());
        rend.paint(c, inline);  */
        BoxRendering.paint(c, inline);
        c.translate(-line.x, -(line.y + (line.baseline - inline.height)));
    }

    static void paintAbsolute(Context c, Box inline) {
        // Uu.p("paint absolute: " + inline);
        //c.translate(line.x, line.y + (line.baseline - inline.height));
        /*Renderer rend = c.getRenderer(inline.content.getElement());
        rend.paint(c, inline);*/
        BoxRendering.paint(c, inline);
        //c.translate(-line.x, -(line.y + (line.baseline - inline.height)));
    }


    static void paintFloat(Context c, InlineBox inline) {
        // Uu.p("painting a float: " + inline);
        Rectangle oe = c.getExtents();
        c.setExtents(new Rectangle(oe.x, 0, oe.width, oe.height));
        //int xoff = line.Xx + inline.Xx;
        int xoff = 0;
        int yoff = 0;//line.y + ( line.baseline - inline.height );// + inline.y;
        // Uu.p("translating  by: " + xoff + " " + yoff);
        c.translate(xoff, yoff);
        /*Renderer rend = c.getRenderer(inline.content.getElement());
        rend.paint(c, inline);//.sub_block ); */
        BoxRendering.paint(c, inline);
        c.translate(-xoff, -yoff);
        c.setExtents(oe);
    }


    public static void paintSelection(Context c, InlineBox inline, int lx, int ly) {
        if (c.inSelection(inline)) {
            int dw = inline.width - 2;
            int xoff = 0;
            if (c.getSelectionEnd() == inline) {
                dw = c.getSelectionEndX();
            }
            if (c.getSelectionStart() == inline) {
                xoff = c.getSelectionStartX();
            }
            c.getGraphics().setColor(new Color(200, 200, 255));
            ((Graphics2D) c.getGraphics()).setPaint(new GradientPaint(0, 0, new Color(235, 235, 255),
                    0, inline.height / 2, new Color(190, 190, 235),
                    true));
            LineMetrics lm = c.getTextRenderer().getLineMetrics(c.getGraphics(), inline.getFont(), "Test");
            int top = ly + inline.y - (int) Math.ceil(lm.getAscent());
            int height = (int) Math.ceil(lm.getAscent() + lm.getDescent());
            c.getGraphics().fillRect(lx + inline.x + xoff,
                    top,
                    dw - xoff,
                    height);
        }
    }


    public static void paintText(Context c, int lx, int ly, int ix, int iy, InlineBox inline) {
        String text = inline.getSubstring();
        Graphics g = c.getGraphics();
        //adjust font for current settings
        Font oldfont = c.getGraphics().getFont();
        c.getGraphics().setFont(inline.getFont());
        Color oldcolor = c.getGraphics().getColor();
        c.getGraphics().setColor(c.getCurrentStyle().getColor());
        Font cur_font = c.getGraphics().getFont();
        LineMetrics lm = c.getTextRenderer().getLineMetrics(c.getGraphics(), cur_font, text);

        iy -= (int) lm.getDescent();
        
        //draw the line
        if (text != null && text.length() > 0) {
            c.getTextRenderer().drawString(c.getGraphics(), text, ix, iy);
        }

        //draw any text decoration
        int stringWidth = (int) Math.ceil(c.getTextRenderer().
                getLogicalBounds(c.getGraphics(),
                        c.getGraphics().getFont(),
                        text).getWidth());

        if (inline.underline) {
            float down = lm.getUnderlineOffset();
            float thick = lm.getUnderlineThickness();
            g.fillRect(ix, iy - (int) down, stringWidth, (int) thick);
        }

        if (inline.strikethrough) {
            float down = lm.getStrikethroughOffset();
            float thick = lm.getStrikethroughThickness();
            g.fillRect(ix, iy + (int) down, stringWidth, (int) thick);
        }

        if (inline.overline) {
            float down = lm.getAscent();
            float thick = lm.getUnderlineThickness();
            g.fillRect(ix, iy - (int) down, stringWidth, (int) thick);
        }

        c.getGraphics().setColor(oldcolor);
        if (c.debugDrawFontMetrics()) {
            g.setColor(Color.red);
            g.drawLine(ix, iy, ix + inline.width, iy);
            iy += (int) Math.ceil(lm.getDescent());
            g.drawLine(ix, iy, ix + inline.width, iy);
            iy -= (int) Math.ceil(lm.getDescent());
            iy -= (int) Math.ceil(lm.getAscent());
            g.drawLine(ix, iy, ix + inline.width, iy);
        }

        // restore the old font
        c.getGraphics().setFont(oldfont);
    }

    public static void paintPadding(Context c, LineBox line, InlineBox inline) {
        //Uu.p("painting border: " + inline.border);
        // paint the background
        int padding_xoff = 0;
        int padding_yoff = inline.totalTopPadding(c.getCurrentStyle());

        int ty = line.baseline - inline.y - inline.height - padding_yoff + line.y;

        LineMetrics lm = c.getTextRenderer().getLineMetrics(c.getGraphics(), inline.getFont(), inline.getSubstring());
        ty += (int) lm.getDescent();
        c.translate(-padding_xoff, ty);
        int old_width = inline.width;
        int old_height = inline.height;
        inline.height += inline.totalVerticalPadding(c.getCurrentStyle());
        BoxRendering.paintBackground(c, inline);
        BoxRendering.paintBorder(c, inline);
        inline.width = old_width;
        inline.height = old_height;
        c.translate(+padding_xoff, -ty);
    }
}


