// Deterministic LeoVer asset generator — exterior white flood-fill, crop, brand hue normalize.
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.IO;
using System.Runtime.InteropServices;

public static class LeoverAssetGen
{
    public static int Main(string[] args)
    {
        string repo = args.Length > 0 ? args[0] : Directory.GetCurrentDirectory();
        string srcPath = Path.Combine(repo, @"app\src\main\res\drawable\logo_leover.jpg");
        if (!File.Exists(srcPath)) throw new FileNotFoundException(srcPath);

        string docsLogo = Path.Combine(repo, @"docs\08-marca\assets\productivos\logo-principal");
        string docsIso = Path.Combine(repo, @"docs\08-marca\assets\productivos\isotipo");
        string docsLaunch = Path.Combine(repo, @"docs\08-marca\assets\productivos\launcher");
        string docsMono = Path.Combine(repo, @"docs\08-marca\assets\productivos\monocromatico");
        string docsSplash = Path.Combine(repo, @"docs\08-marca\assets\productivos\splash");
        string docsPreview = Path.Combine(repo, @"docs\08-marca\assets\productivos\previews");
        string androidNodpi = Path.Combine(repo, @"app\src\main\res\drawable-nodpi");
        string androidDrawable = Path.Combine(repo, @"app\src\main\res\drawable");
        foreach (var d in new[] { docsLogo, docsIso, docsLaunch, docsMono, docsSplash, docsPreview, androidNodpi })
            Directory.CreateDirectory(d);

        Console.WriteLine("Source: " + srcPath);
        using (var src = new Bitmap(srcPath))
        using (var transparent = RemoveExteriorWhite(src))
        {
            NormalizeBrandColors(transparent);
            var bounds = OpaqueBounds(transparent);
            Console.WriteLine("Bounds: " + bounds);
            int gapY = FindGapY(transparent, bounds);
            Console.WriteLine("GapY: " + gapY);

            using (var fullCrop = Crop(transparent, bounds))
            using (var isoLoose = Crop(transparent, Rectangle.FromLTRB(bounds.Left, bounds.Top, bounds.Right, gapY - 4)))
            using (var wordLoose = Crop(transparent, Rectangle.FromLTRB(bounds.Left, gapY + 4, bounds.Right, bounds.Bottom)))
            using (var iso = Crop(isoLoose, OpaqueBounds(isoLoose)))
            using (var word = Crop(wordLoose, OpaqueBounds(wordLoose)))
            using (var vertical = ResizeMaxSide(fullCrop, 1600))
            using (var horizontal = ComposeHorizontal(iso, word, 1800))
            using (var wordmark = ResizeMinWidth(word, 1200))
            using (var isotipo1024 = FitCenteredSquare(iso, 1024, 0.86))
            using (var launcherFg = MakeLauncherForeground(iso, 1024))
            using (var mono = MakeMonochrome(isotipo1024))
            {
                Color cream = Color.FromArgb(255, 255, 246, 234);
                SavePng(vertical, Path.Combine(docsLogo, "leover-logo-vertical-v1.png"));
                SavePng(horizontal, Path.Combine(docsLogo, "leover-logo-horizontal-v1.png"));
                SavePng(wordmark, Path.Combine(docsLogo, "leover-wordmark-v1.png"));
                SavePng(isotipo1024, Path.Combine(docsIso, "leover-isotipo-v1.png"));
                SavePng(isotipo1024, Path.Combine(docsIso, "leover-isotipo-transparente-v1.png"));
                SavePng(launcherFg, Path.Combine(docsLaunch, "leover-launcher-foreground-v1.png"));

                using (var launcherPreview = ComposeOnBg(launcherFg, cream, 1024, 1024, 1.0))
                using (var roundPreview = RoundPreview(launcherFg, cream, 1024))
                using (var splash = ComposeOnBg(vertical, cream, 1600, 1600, 0.62))
                {
                    SavePng(launcherPreview, Path.Combine(docsLaunch, "leover-launcher-preview-v1.png"));
                    SavePng(roundPreview, Path.Combine(docsLaunch, "leover-launcher-round-preview-v1.png"));
                    SavePng(mono, Path.Combine(docsMono, "leover-isotipo-monochrome-v1.png"));
                    SavePng(splash, Path.Combine(docsSplash, "leover-splash-v1.png"));

                    using (var p1 = ComposeOnBg(vertical, Color.White, 1600, 1600, 0.7))
                    using (var p2 = ComposeOnBg(vertical, cream, 1600, 1600, 0.7))
                    using (var p3 = ComposeOnBg(isotipo1024, cream, 1024, 1024, 0.85))
                    using (var p6 = ComposeOnBg(mono, Color.FromArgb(255, 47, 58, 55), 1024, 1024, 0.85))
                    {
                        SavePng(p1, Path.Combine(docsPreview, "preview-logo-vertical-white.png"));
                        SavePng(p2, Path.Combine(docsPreview, "preview-logo-vertical-cream.png"));
                        SavePng(p3, Path.Combine(docsPreview, "preview-isotipo-cream.png"));
                        SavePng(launcherPreview, Path.Combine(docsPreview, "preview-launcher-square.png"));
                        SavePng(roundPreview, Path.Combine(docsPreview, "preview-launcher-round.png"));
                        SavePng(p6, Path.Combine(docsPreview, "preview-monochrome.png"));
                        SavePng(splash, Path.Combine(docsPreview, "preview-splash.png"));
                    }

                    File.Copy(Path.Combine(docsLogo, "leover-logo-vertical-v1.png"), Path.Combine(androidNodpi, "leover_logo_official.png"), true);
                    File.Copy(Path.Combine(docsLogo, "leover-logo-horizontal-v1.png"), Path.Combine(androidNodpi, "leover_logo_horizontal.png"), true);
                    File.Copy(Path.Combine(docsIso, "leover-isotipo-transparente-v1.png"), Path.Combine(androidNodpi, "leover_isotype_official.png"), true);
                    File.Copy(Path.Combine(docsSplash, "leover-splash-v1.png"), Path.Combine(androidNodpi, "leover_splash_logo.png"), true);
                    File.Copy(Path.Combine(docsLaunch, "leover-launcher-foreground-v1.png"), Path.Combine(androidDrawable, "leover_launcher_foreground.png"), true);
                    File.Copy(Path.Combine(docsMono, "leover-isotipo-monochrome-v1.png"), Path.Combine(androidDrawable, "leover_isotype_monochrome.png"), true);
                }
            }
        }
        Console.WriteLine("DONE");
        return 0;
    }

    static void SavePng(Bitmap bmp, string path)
    {
        bmp.Save(path, ImageFormat.Png);
        Console.WriteLine("Wrote " + path + " (" + bmp.Width + "x" + bmp.Height + ")");
    }

    static bool NearWhite(byte r, byte g, byte b, int thresh = 248)
    {
        return r >= thresh && g >= thresh && b >= thresh;
    }

    static Bitmap RemoveExteriorWhite(Bitmap src)
    {
        int w = src.Width, h = src.Height;
        var dst = new Bitmap(w, h, PixelFormat.Format32bppArgb);
        using (var g = Graphics.FromImage(dst)) g.DrawImage(src, 0, 0, w, h);

        var data = dst.LockBits(new Rectangle(0, 0, w, h), ImageLockMode.ReadWrite, PixelFormat.Format32bppArgb);
        int stride = data.Stride;
        int bytes = Math.Abs(stride) * h;
        byte[] buf = new byte[bytes];
        Marshal.Copy(data.Scan0, buf, 0, bytes);

        bool[] visited = new bool[w * h];
        var q = new Queue<int>();
        Action<int, int> tryEnqueue = (x, y) =>
        {
            if (x < 0 || y < 0 || x >= w || y >= h) return;
            int i = y * w + x;
            if (visited[i]) return;
            int o = y * stride + x * 4;
            byte b = buf[o], g = buf[o + 1], r = buf[o + 2];
            if (!NearWhite(r, g, b)) return;
            visited[i] = true;
            q.Enqueue(i);
        };

        for (int x = 0; x < w; x++) { tryEnqueue(x, 0); tryEnqueue(x, h - 1); }
        for (int y = 0; y < h; y++) { tryEnqueue(0, y); tryEnqueue(w - 1, y); }

        int[] dx = { 1, -1, 0, 0 };
        int[] dy = { 0, 0, 1, -1 };
        while (q.Count > 0)
        {
            int i = q.Dequeue();
            int x = i % w, y = i / w;
            int o = y * stride + x * 4;
            buf[o] = 0; buf[o + 1] = 0; buf[o + 2] = 0; buf[o + 3] = 0;
            for (int k = 0; k < 4; k++) tryEnqueue(x + dx[k], y + dy[k]);
        }

        // Soft fringe
        for (int y = 0; y < h; y++)
        {
            for (int x = 0; x < w; x++)
            {
                int o = y * stride + x * 4;
                if (buf[o + 3] == 0) continue;
                byte r = buf[o + 2], g = buf[o + 1], b = buf[o];
                int whiteness = Math.Min(r, Math.Min(g, b));
                if (whiteness < 230) continue;
                bool hasTrans = false;
                for (int k = 0; k < 4; k++)
                {
                    int nx = x + dx[k], ny = y + dy[k];
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) { hasTrans = true; break; }
                    if (buf[ny * stride + nx * 4 + 3] == 0) { hasTrans = true; break; }
                }
                if (!hasTrans) continue;
                int a = (int)Math.Round(255.0 * (1.0 - ((whiteness - 230) / 25.0)));
                if (a < 12) { buf[o] = buf[o + 1] = buf[o + 2] = buf[o + 3] = 0; }
                else buf[o + 3] = (byte)Math.Max(0, Math.Min(255, a));
            }
        }

        Marshal.Copy(buf, 0, data.Scan0, bytes);
        dst.UnlockBits(data);
        return dst;
    }

    static void NormalizeBrandColors(Bitmap bmp)
    {
        int w = bmp.Width, h = bmp.Height;
        var data = bmp.LockBits(new Rectangle(0, 0, w, h), ImageLockMode.ReadWrite, PixelFormat.Format32bppArgb);
        int stride = data.Stride;
        byte[] buf = new byte[Math.Abs(stride) * h];
        Marshal.Copy(data.Scan0, buf, 0, buf.Length);
        for (int y = 0; y < h; y++)
        {
            for (int x = 0; x < w; x++)
            {
                int o = y * stride + x * 4;
                if (buf[o + 3] < 8) continue;
                byte b = buf[o], g = buf[o + 1], r = buf[o + 2];
                int max = Math.Max(r, Math.Max(g, b));
                int min = Math.Min(r, Math.Min(g, b));
                if (max - min < 18) continue;
                double lum = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0;
                if (r > g + 15 && r > b + 25 && r > 90)
                {
                    double t = Math.Min(1.0, Math.Max(0.0, (lum - 0.35) / 0.45));
                    int tr = 255, tg = (int)(122 + (166 - 122) * t), tb = (int)(0 + 77 * t);
                    buf[o + 2] = Clamp(r * 0.45 + tr * 0.55);
                    buf[o + 1] = Clamp(g * 0.45 + tg * 0.55);
                    buf[o] = Clamp(b * 0.45 + tb * 0.55);
                }
                else if (g > r + 10 && g > b + 10 && g > 70)
                {
                    double t = Math.Min(1.0, Math.Max(0.0, (lum - 0.25) / 0.45));
                    int tr = (int)(36 + (73 - 36) * t);
                    int tg = (int)(122 + (183 - 122) * t);
                    int tb = (int)(61 + (73 - 61) * t);
                    buf[o + 2] = Clamp(r * 0.45 + tr * 0.55);
                    buf[o + 1] = Clamp(g * 0.45 + tg * 0.55);
                    buf[o] = Clamp(b * 0.45 + tb * 0.55);
                }
            }
        }
        Marshal.Copy(buf, 0, data.Scan0, buf.Length);
        bmp.UnlockBits(data);
    }

    static byte Clamp(double v)
    {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return (byte)Math.Round(v);
    }

    static Rectangle OpaqueBounds(Bitmap bmp, int aMin = 8)
    {
        int w = bmp.Width, h = bmp.Height;
        var data = bmp.LockBits(new Rectangle(0, 0, w, h), ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
        int stride = data.Stride;
        byte[] buf = new byte[Math.Abs(stride) * h];
        Marshal.Copy(data.Scan0, buf, 0, buf.Length);
        bmp.UnlockBits(data);
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if (buf[y * stride + x * 4 + 3] >= aMin)
                {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
        if (maxX < 0) throw new Exception("No opaque pixels");
        return Rectangle.FromLTRB(minX, minY, maxX + 1, maxY + 1);
    }

    static int FindGapY(Bitmap bmp, Rectangle bounds)
    {
        int w = bmp.Width;
        var data = bmp.LockBits(new Rectangle(0, 0, w, bmp.Height), ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
        int stride = data.Stride;
        byte[] buf = new byte[Math.Abs(stride) * bmp.Height];
        Marshal.Copy(data.Scan0, buf, 0, buf.Length);
        bmp.UnlockBits(data);
        int bestY = -1, best = int.MaxValue;
        int y0 = bounds.Top + (int)(bounds.Height * 0.45);
        int y1 = bounds.Top + (int)(bounds.Height * 0.85);
        for (int y = y0; y < y1; y++)
        {
            int count = 0;
            for (int x = bounds.Left; x < bounds.Right; x++)
                if (buf[y * stride + x * 4 + 3] >= 16) count++;
            if (count < best) { best = count; bestY = y; }
        }
        return bestY;
    }

    static Bitmap Crop(Bitmap src, Rectangle rect)
    {
        var outBmp = new Bitmap(rect.Width, rect.Height, PixelFormat.Format32bppArgb);
        using (var g = Graphics.FromImage(outBmp))
            g.DrawImage(src, new Rectangle(0, 0, rect.Width, rect.Height), rect, GraphicsUnit.Pixel);
        return outBmp;
    }

    static Bitmap ResizeMaxSide(Bitmap src, int minMaxSide)
    {
        int maxSide = Math.Max(src.Width, src.Height);
        if (maxSide >= minMaxSide) return Clone32(src);
        double scale = minMaxSide / (double)maxSide;
        return Resize(src, (int)Math.Round(src.Width * scale), (int)Math.Round(src.Height * scale));
    }

    static Bitmap ResizeMinWidth(Bitmap src, int minWidth)
    {
        if (src.Width >= minWidth) return Clone32(src);
        double scale = minWidth / (double)src.Width;
        return Resize(src, minWidth, (int)Math.Round(src.Height * scale));
    }

    static Bitmap Resize(Bitmap src, int nw, int nh)
    {
        var outBmp = new Bitmap(nw, nh, PixelFormat.Format32bppArgb);
        using (var g = Graphics.FromImage(outBmp))
        {
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.SmoothingMode = SmoothingMode.HighQuality;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            g.DrawImage(src, 0, 0, nw, nh);
        }
        return outBmp;
    }

    static Bitmap Clone32(Bitmap src)
    {
        var outBmp = new Bitmap(src.Width, src.Height, PixelFormat.Format32bppArgb);
        using (var g = Graphics.FromImage(outBmp)) g.DrawImage(src, 0, 0, src.Width, src.Height);
        return outBmp;
    }

    static Bitmap FitCenteredSquare(Bitmap src, int size, double fill)
    {
        var outBmp = new Bitmap(size, size, PixelFormat.Format32bppArgb);
        using (var g = Graphics.FromImage(outBmp))
        {
            g.Clear(Color.Transparent);
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.SmoothingMode = SmoothingMode.HighQuality;
            double scale = Math.Min((size * fill) / src.Width, (size * fill) / src.Height);
            int dw = (int)Math.Round(src.Width * scale);
            int dh = (int)Math.Round(src.Height * scale);
            g.DrawImage(src, (size - dw) / 2, (size - dh) / 2, dw, dh);
        }
        return outBmp;
    }

    static Bitmap ComposeHorizontal(Bitmap iso, Bitmap word, int minWidth)
    {
        int gap = (int)Math.Round(iso.Height * 0.08);
        double wordScale = (iso.Height * 0.55) / word.Height;
        int ww = (int)Math.Round(word.Width * wordScale);
        int wh = (int)Math.Round(word.Height * wordScale);
        int totalW = iso.Width + gap + ww;
        int totalH = Math.Max(iso.Height, wh);
        var canvas = new Bitmap(totalW, totalH, PixelFormat.Format32bppArgb);
        using (var g = Graphics.FromImage(canvas))
        {
            g.Clear(Color.Transparent);
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.DrawImage(iso, 0, (totalH - iso.Height) / 2, iso.Width, iso.Height);
            g.DrawImage(word, iso.Width + gap, (totalH - wh) / 2, ww, wh);
        }
        var resized = ResizeMinWidth(canvas, minWidth);
        canvas.Dispose();
        return resized;
    }

    static Bitmap MakeLauncherForeground(Bitmap iso, int size)
    {
        var outBmp = new Bitmap(size, size, PixelFormat.Format32bppArgb);
        using (var g = Graphics.FromImage(outBmp))
        {
            g.Clear(Color.Transparent);
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.SmoothingMode = SmoothingMode.HighQuality;
            double safe = size * 0.64;
            double scale = Math.Min(safe / iso.Width, safe / iso.Height);
            int dw = (int)Math.Round(iso.Width * scale);
            int dh = (int)Math.Round(iso.Height * scale);
            g.DrawImage(iso, (size - dw) / 2, (size - dh) / 2, dw, dh);
        }
        return outBmp;
    }

    static Bitmap MakeMonochrome(Bitmap src)
    {
        int w = src.Width, h = src.Height;
        var outBmp = new Bitmap(w, h, PixelFormat.Format32bppArgb);
        var sData = src.LockBits(new Rectangle(0, 0, w, h), ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
        var dData = outBmp.LockBits(new Rectangle(0, 0, w, h), ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
        int stride = sData.Stride;
        byte[] sbuf = new byte[Math.Abs(stride) * h];
        byte[] dbuf = new byte[sbuf.Length];
        Marshal.Copy(sData.Scan0, sbuf, 0, sbuf.Length);
        for (int i = 0; i < sbuf.Length; i += 4)
        {
            byte a = sbuf[i + 3];
            if (a < 20) { dbuf[i] = dbuf[i + 1] = dbuf[i + 2] = dbuf[i + 3] = 0; }
            else { dbuf[i] = dbuf[i + 1] = dbuf[i + 2] = 255; dbuf[i + 3] = a; }
        }
        Marshal.Copy(dbuf, 0, dData.Scan0, dbuf.Length);
        src.UnlockBits(sData);
        outBmp.UnlockBits(dData);
        return outBmp;
    }

    static Bitmap ComposeOnBg(Bitmap fg, Color bg, int cw, int ch, double scaleFill)
    {
        var outBmp = new Bitmap(cw, ch, PixelFormat.Format32bppArgb);
        using (var g = Graphics.FromImage(outBmp))
        {
            g.Clear(bg);
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.SmoothingMode = SmoothingMode.HighQuality;
            double scale = Math.Min((cw * scaleFill) / fg.Width, (ch * scaleFill) / fg.Height);
            int dw = (int)Math.Round(fg.Width * scale);
            int dh = (int)Math.Round(fg.Height * scale);
            g.DrawImage(fg, (cw - dw) / 2, (ch - dh) / 2, dw, dh);
        }
        return outBmp;
    }

    static Bitmap RoundPreview(Bitmap fg, Color bg, int size)
    {
        var outBmp = new Bitmap(size, size, PixelFormat.Format32bppArgb);
        using (var g = Graphics.FromImage(outBmp))
        {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.Clear(Color.Transparent);
            using (var path = new GraphicsPath())
            {
                path.AddEllipse(0, 0, size - 1, size - 1);
                g.SetClip(path);
                g.Clear(bg);
                g.DrawImage(fg, 0, 0, size, size);
                g.ResetClip();
            }
        }
        return outBmp;
    }
}
