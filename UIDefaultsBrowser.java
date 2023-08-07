import static java.awt.RenderingHints.*;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.Painter;
import javax.swing.JComponent;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.reflect.Array;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.TreeMap;
import java.util.Objects;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class UIDefaultsBrowser {
    private static final System.Logger LOG = System.getLogger(UIDefaultsBrowser.class.getName());

    private static final int PREVIEW_LARGE_WIDTH = 512;
    private static final int PREVIEW_LARGE_HEIGHT = PREVIEW_LARGE_WIDTH;

    private int IMAGE_COUNT = 0;

    private static boolean isPreviewLarge(int width, int height){
        return width > PREVIEW_LARGE_WIDTH || PREVIEW_LARGE_HEIGHT > 512;
    }

    public static final void main(String... args) {
        if(args.length != 1){
            System.out.println("Usage");
            System.out.println("java UIDefaultsBrowser.java [output]");
        } else {
            SwingUtilities.invokeLater(() -> new UIDefaultsBrowser(args[0]));
        }
    }

    public UIDefaultsBrowser(String output){
        var selectedLookAndFeel = Objects.requireNonNull(UIManager.getLookAndFeel(), "disallow null-laf");
        var selectedLookAndFeelClassName = selectedLookAndFeel.getClass().getName();
        for (var laf : UIManager.getInstalledLookAndFeels()) {
            var className=laf.getClassName();
            if(className.equals(selectedLookAndFeelClassName)){
                System.out.println(className + " [SELECTED]");
            } else {
                System.out.println(className);
            }
        }
        var lookAndFeelDefaults = UIManager.getLookAndFeelDefaults();
        var componentDefaults = new TreeMap<String, Object>(String::compareTo);
        for (var entry : lookAndFeelDefaults.entrySet()) {
            componentDefaults.put(entry.getKey().toString(), entry.getValue());
        }
        // split out UIs
        var uiClasses = new TreeMap<String, Object>(String::compareTo);
        for (var entry : componentDefaults.entrySet()) {
            var key=entry.getKey();
            if (key.endsWith("UI")) {
                uiClasses.put(key, entry.getValue());
            }
        }
        for (var key : uiClasses.keySet()) {
            componentDefaults.remove(key);
        }

        // write html file
        var base = Paths.get(output, selectedLookAndFeelClassName);
        try {
            Files.createDirectories(base);
        } catch (IOException e) {
            e.printStackTrace();
        }

        var htmlFile = base.resolve("UIDefaults.html");
        System.out.println("Outputing to " + htmlFile.toAbsolutePath());
        var stringWriter = new StringWriter(200);
        try (var html = new PrintWriter(stringWriter)) {
            html.println("""
            <!DOCTYPE html>
            <html lang="en-US">
            <head>
            <style>
            .generic_to_string {
                word-break: break-word;
            }
            </style>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width">
            <title>UIDefaults browser - browsing UIDefaults of %1$s</title>
            <link href="style.css" rel="stylesheet" type="text/css"/>
            </head>
            <body>
            <hgroup id="title">
            <h1>UIDefaults browser</h1>
            <p>browsing UIDefaults of <code>%1$s</code>.</p>
            </hgroup>
            """.formatted(selectedLookAndFeelClassName));

            html.println("""
            <header>
            <figure>
            <figcaption>Table of contents</figcaption>
            """);

            var titleComponents="Components";
            var titleUIClasses = "UI Classes";
            printTableOfContent(
                titleComponents,
                titleUIClasses,
                html
            );

            html.println("""
            </figure>
            </header>
            <main>
            """);

            printTable(titleComponents, componentDefaults, html, base, lookAndFeelDefaults);
            printTable(titleUIClasses, uiClasses, html, base, lookAndFeelDefaults);

            html.println("""
            </main>
            <footer id="footer">
            <p>If the value is <mark>UIDefaults.ActiveValue</mark> or <mark>UIDefaults.LazyValue</mark>, they are marked.</p>
            </footer>
            </body>
            </html>
            """);

            Files.write(htmlFile, java.util.Arrays.asList(stringWriter.getBuffer()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void printTableOfContent(
        String titleComponents,
        String titleUIClasses,
        PrintWriter html
    ) throws Exception {
        html.println("<ul>");
        html.println("<li><a href=\"#" + titleComponents + "\">" + titleComponents + "</a></li>");
        html.println("<li><a href=\"#" + titleUIClasses + "\">" + titleUIClasses + "</a>");
        html.println("<li><a href=\"#footer\">Note</a>");
        html.println("</ul>");
    }

    private void printTable(String caption, Map<String, Object> map, PrintWriter html, Path base, UIDefaults table) throws Exception {
        html.println("""
        <table><caption id="%s">%s</caption>
        <thead><tr><th>Key</th><th>Value</th><th>Preview</th></tr></thead>
        <tbody>
        """.formatted(caption, caption));
        for (var key : map.keySet()) {
            printRow(base, html, key, map.get(key), table);
        }
        html.println("</tbody></table>");
    }

    private void printRow(Path base, PrintWriter html, Object key, Object value, UIDefaults table) throws Exception {
        html.println("<tr><td><code>%s</code></td>".formatted(key));
        if(value instanceof UIDefaults.ActiveValue activeValue) {
            var live = activeValue.createValue(table);
            html.println("<td><mark>%s</mark></td><td>&nbsp;</td>".formatted(activeValue));
            printRow(base, html, activeValue.toString(), live, table);
        } else if (value instanceof UIDefaults.LazyValue lazyValue) {
            var live = lazyValue.createValue(table);
            html.println("<td><mark>%s</mark></td><td>&nbsp;</td>".formatted(lazyValue));
            printRow(base, html, lazyValue.toString(), live, table);
        } else if (value instanceof Color color) {
            printColor(html, color);
        } else if (value instanceof Font font) {
            printFont(base, html, font);
        } else if (value instanceof Dimension dim) {
            printDimension(base, html, dim);
        } else if (value instanceof Insets insets) {
            printInsets(base, html, insets);
        } else if (value instanceof Border border) {
            printBorder(base, html, border);
        } else if (value instanceof Painter painter) {
            printPainter(base, html, painter);
        } else if (value instanceof InputMap inputMap) {
            printInputMap(html, inputMap);
        } else if (value instanceof Icon icon) {
            printIcon(base, html, icon);
        } else if(value instanceof Number number) {
            var s=Objects.toString(number);
            html.println("<td>%s</td><td>&nbsp;</td>".formatted(s));
        } else if(value instanceof Boolean b) {
            var s=Objects.toString(b);
            html.println("<td>%s</td><td>&nbsp;</td>".formatted(s));
        } else if (value != null && value.getClass().isArray()) {
            printArray(html, value);
        } else {
            var s=Objects.toString(value);
            html.println("""
            <td class="generic_to_string">%s</td><td>&nbsp;</td>
            """.formatted(s));
        }
        html.println("</tr>");
    }

    private void printColor(PrintWriter html, Color color) {
        var webColor = getWebColor(color);
        var negativeColor = getNegativeWebColor(color);
        var colorTuple = getColorTuple(color);
        html.println("""
        <td><code>%1$s</code></td>
        <td title="#%3$s" style="background-color: #%3$s;color: #%4$s;">#%3$s</td>
        """.formatted(color, colorTuple, webColor, negativeColor));
    }

    private void printPainter(Path base, PrintWriter html, Painter painter) throws Exception {
        html.println("<td>%s</td>".formatted(painter.getClass().getTypeName()));
        int w=25,h=25;
        var img = new BufferedImage(w,h, BufferedImage.TYPE_INT_ARGB);
        var g2 = img.createGraphics();
        var old = g2.getComposite();
        g2.setComposite(AlphaComposite.Clear);
        g2.fillRect(0,0,w,h);
        g2.setComposite(old);
        boolean skipPaint=false;
        try {
            painter.paint(g2, makeJComponent(w,h),w,h);
        } catch (Exception e) {
            LOG.log(Level.ERROR, "skip paint painter");
            g2.drawString("skip paint", 0, 0);
            html.println("<td>&nbsp;</td>");
        }
        g2.dispose();
        html.println("<td>%s</td>".formatted(saveImage(base, img, skipPaint)));
    }

    private void printFont(Path base, PrintWriter html, Font font) throws Exception {
        int w = 320;
        int h = font.getSize() * 2;
        var img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var g2 = img.createGraphics();
        g2.addRenderingHints(Map.of(
            KEY_TEXT_ANTIALIASING, VALUE_TEXT_ANTIALIAS_ON
        ));
        var old = g2.getComposite();
        g2.setComposite(AlphaComposite.Clear);
        var frc = g2.getFontRenderContext();
        var layout = new TextLayout("the quick brown fox jumps over the crazy dog", font, frc);
        float x = ((float)w) / 2.0f - layout.getAdvance() / 2.0f;
        float y = layout.getAscent() + layout.getDescent() + layout.getLeading();
        var bounds=layout.getBounds();
        bounds.setRect(bounds.getX() + (double)x,
                       bounds.getY() + (double)y,
                       bounds.getWidth(),
                       bounds.getHeight());
        g2.draw(bounds);
        g2.setComposite(old);
        g2.setColor(Color.BLACK);
        layout.draw(g2, x, y);
        g2.dispose();

        html.println("<td>%s</td><td>%s</td>".formatted(font, saveImage(base, img, false)));
    }

    private void printInsets(Path base, PrintWriter html, Insets insets) throws Exception {
        int w = 50+insets.left+insets.right;
        int h = 20+insets.top+insets.bottom;
        var img = new BufferedImage(w,h, BufferedImage.TYPE_INT_ARGB);
        var g2 = img.createGraphics();
        var old = g2.getComposite();
        g2.setComposite(AlphaComposite.Clear);
        g2.fillRect(0,0,w,h);
        g2.setComposite(old);
        g2.setColor(Color.BLACK);
        g2.drawRect(insets.left, insets.top, 49,19);
        g2.setColor(Color.RED);
        g2.drawRect(0,0,w-1,h-1);
        g2.dispose();

        html.println("<td>%s</td><td>%s</td>".formatted(insets, saveImage(base, img, false)));
    }

    private void printBorder(Path base, PrintWriter html, Border border) throws Exception {
        var insets = border.getBorderInsets(null);
        html.println("<td>%s</td>".formatted(insets));
        int w = 50+insets.left+insets.right;
        int h = 20+insets.top+insets.bottom;
        try {
            var img = new BufferedImage(w,h, BufferedImage.TYPE_INT_ARGB);
            var g2 = img.createGraphics();
            var old = g2.getComposite();
            g2.setComposite(AlphaComposite.Clear);
            g2.fillRect(0,0,w,h);
            g2.setComposite(old);
            g2.setColor(Color.RED);
            g2.fillRect(insets.left, insets.top, 49,19);
            boolean skipPaint=false;
            try {
                // FIXME: borderの種類ごとにcomponentを渡す必要がある
                border.paintBorder(makeJComponent(w,h),g2, 0,0,w,h);
            }catch(Exception e){
                LOG.log(Level.ERROR, "skip paint border");
                g2.drawString("skip paint", 0, 0);
                skipPaint=true;
            }
            g2.dispose();
            html.println("<td>%s</td>".formatted(saveImage(base, img, skipPaint)));
        } catch (Exception e) {
            e.printStackTrace();
            html.println("<td>&nbsp;</td>");
        }
    }

    private void printDimension(Path base, PrintWriter html, Dimension dim) throws Exception {
        html.println("<td>%s</td>".formatted(dim));
        int w = dim.width;
        int h = dim.height;
        if (w==0 || h==0){
            html.println("<td>&nbsp;</td>");
        } else {
            var img = new BufferedImage(w,h, BufferedImage.TYPE_INT_ARGB);
            var g2 = img.createGraphics();
            var old = g2.getComposite();
            g2.setComposite(AlphaComposite.Clear);
            g2.fillRect(0,0,w,h);
            g2.setComposite(old);
            g2.setColor(Color.RED);
            g2.drawRect(0,0,w-1,h-1);
            g2.dispose();
            html.println("<td>%s</td>".formatted(saveImage(base, img, false)));
        }
    }

    private void printIcon(Path base, PrintWriter html, Icon icon) throws Exception {
        var w=icon.getIconWidth();
        var h=icon.getIconHeight();
        BufferedImage img=null;
        try {
            img = new BufferedImage(w,h, BufferedImage.TYPE_INT_ARGB);
        } catch(IllegalArgumentException iae){
            LOG.log(Level.ERROR, iae);
            w=16;
            h=16;
            img = new BufferedImage(w,h, BufferedImage.TYPE_INT_ARGB);
        }
        var g2 = img.createGraphics();
        var old = g2.getComposite();
        g2.setComposite(AlphaComposite.Clear);
        g2.fillRect(0,0,w, h);
        g2.setComposite(old);
        boolean skipPaint=false;
        try {
            // FIXME: iconの種類ごとにcomponentを渡す必要がある
            icon.paintIcon(makeJComponent(w, h),g2, 0, 0);
        }catch(Exception e){
            LOG.log(Level.ERROR, "skip paint icon");
            g2.drawString("skip paint", 0, 0);
            skipPaint=true;
        }
        g2.dispose();

        html.println("""
        <td>Icon %s * %s</td><td>%s</td>
        """.formatted(icon.getIconWidth(), icon.getIconHeight(), saveImage(base, img, skipPaint)));
    }

    private void printInputMap(PrintWriter html, InputMap inputMap) {
        html.println("""
        <td>
        <details>
        <summary>%s</summary>
        <ul>
        """.formatted(inputMap.getClass().getTypeName()));
        for (var key : inputMap.allKeys()){
            var binding = inputMap.get(key);
            html.println("""
            <li><code>%s</code> : %s
            """.formatted(key, binding));
        }
        html.println("""
        </ul>
        </details>
        </td>
        <td>&nbsp;</td>
        """);
    }

    private void printArray(PrintWriter html, Object value) {
        html.println("""
        <td>
        <details>
        <summary>%s</summary>
        <ul>
        """.formatted(value.getClass().getTypeName()));
        for(var i=0; i<Array.getLength​(value); i++){
            var c = Array.get(value, i);
            html.println("<li>"+ c);
        }
        html.println("""
        </ul>
        </details>
        </td>
        <td>&nbsp;</td>
        """);
    }

    private String saveImage(Path base, BufferedImage img, boolean skipPaint) throws Exception {
        var path = base.resolve("images");
        Files.createDirectories(path);
        var name = "img_" + (IMAGE_COUNT++) + ".png";
        path = path.resolve(name);
        if(Files.notExists(path)) {
            Files.createFile(path);
        }
        var imgFile = path.toFile();
        ImageIO.write(img,"png",imgFile);

        int width=img.getWidth();
        int height=img.getHeight();
        if(skipPaint){
            return """
            <img src="%s" alt="%s"><strong>skip paint</strong>
            """.formatted(base.relativize(path), name);
        } else if(isPreviewLarge(width, height)) {
            LOG.log(Level.INFO, """
            large preview converted to link: %s * %s""".formatted(width, height));
            return """
            <a href="%s" title="%s">show large preview</a>
            """.formatted(base.relativize(path), name);
        } else {
            return """
            <img src="%s" alt="%s">
            """.formatted(base.relativize(path), name);
        }
    }

    private String getColorTuple(Color color) {
        return color.getRed() + "," + color.getGreen() + "," + color.getBlue() + "," + color.getAlpha();
    }

    private String getWebColor(Color color) {
        return getWebColor(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    private String getWebColor(int red, int green, int blue, int alpha) {
        var result = new StringBuilder();

        var num = Integer.toHexString(red);
        if (num.length() == 1) { num = "0" + num; }
        result.append(num);

        num = Integer.toHexString(green);
        if (num.length() == 1) { num = "0" + num; }
        result.append(num);

        num = Integer.toHexString(blue);
        if (num.length() == 1) { num = "0" + num; }
        result.append(num);

        num = Integer.toHexString(alpha);
        if (num.length() == 1) { num = "0" + num; }
        result.append(num);

        return result.toString();
    }

    private String getNegativeWebColor(Color color) {
        return getWebColor(0xff - color.getRed(), 0xff - color.getGreen(), 0xff - color.getBlue(), color.getAlpha());
    }

    private JComponent makeJComponent(int w, int h){
        return new JComponent(){
            @Override
            public int getWidth(){
                return w;
            }
            @Override
            public int getHeight(){
                return h;
            }
            @Override
            public Dimension getSize(Dimension rv){
                return new Dimension(getWidth(), getHeight());
            }
        };
    }
}