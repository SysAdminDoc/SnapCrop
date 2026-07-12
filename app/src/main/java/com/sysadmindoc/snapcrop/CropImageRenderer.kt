package com.sysadmindoc.snapcrop

import android.graphics.*
import kotlin.math.roundToInt

internal object CropImageRenderer {
    private fun applyRedactions(bitmap: Bitmap, redactions: List<RedactionRegion>): Bitmap {
        return ImageRedactor.render(bitmap, redactions)
    }
    
    private fun applyDraw(bitmap: Bitmap, paths: List<DrawPath>): Bitmap {
        val visiblePaths = paths.filter { it.visible }
        if (visiblePaths.isEmpty()) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (dp in visiblePaths) {
            if (dp.points.isEmpty()) continue
            // Bitmap-mutating tools resolve their geometry first because Canvas transforms cannot
            // move the source pixels they sample. Vector layers continue to use Canvas.concat.
            val pixelOperation = dp.isPixelOperation()
            val operationPath = if (pixelOperation) dp.transformedForPixelOperation() else dp
            val layerMatrix = if (pixelOperation) null else dp.transformMatrix()
            val layerSaveCount = if (layerMatrix != null) {
                val sc = canvas.save(); canvas.concat(layerMatrix); sc
            } else -1
            try {
            paint.color = dp.color
            paint.strokeWidth = dp.strokeWidth
            paint.alpha = 255
            paint.pathEffect = if (dp.dashed) android.graphics.DashPathEffect(floatArrayOf(dp.strokeWidth * 3, dp.strokeWidth * 2), 0f) else null
    
            // Line tool — straight line between two points
            if (dp.shapeType == "line" && dp.points.size >= 2) {
                val p1 = dp.points.first(); val p2 = dp.points.last()
                paint.pathEffect = if (dp.dashed) android.graphics.DashPathEffect(floatArrayOf(dp.strokeWidth * 3, dp.strokeWidth * 2), 0f) else null
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
                continue
            }
    
            // Measurement/ruler — line with end ticks and a pixel-distance label
            if (dp.shapeType == "measure" && dp.points.size >= 2) {
                val p1 = dp.points.first(); val p2 = dp.points.last()
                paint.pathEffect = null
                val dist = kotlin.math.hypot((p2.x - p1.x).toDouble(), (p2.y - p1.y).toDouble())
                val angle = kotlin.math.atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble())
                val tick = dp.strokeWidth * 2.5f
                val nx = (-kotlin.math.sin(angle)).toFloat() * tick
                val ny = (kotlin.math.cos(angle)).toFloat() * tick
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
                canvas.drawLine(p1.x - nx, p1.y - ny, p1.x + nx, p1.y + ny, paint)
                canvas.drawLine(p2.x - nx, p2.y - ny, p2.x + nx, p2.y + ny, paint)
                val label = "${dist.roundToInt()} px"
                val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = dp.color
                    textSize = (dp.strokeWidth * 5f).coerceAtLeast(20f)
                    textAlign = Paint.Align.CENTER
                }
                val mx = (p1.x + p2.x) / 2f; val my = (p1.y + p2.y) / 2f - tick - dp.strokeWidth
                val bounds = Rect()
                labelPaint.getTextBounds(label, 0, label.length, bounds)
                val pad = labelPaint.textSize * 0.3f
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC000000.toInt(); style = Paint.Style.FILL }
                canvas.drawRoundRect(
                    mx - bounds.width() / 2f - pad, my + bounds.top - pad,
                    mx + bounds.width() / 2f + pad, my + bounds.bottom + pad,
                    pad.coerceAtMost(8f), pad.coerceAtMost(8f), bgPaint)
                canvas.drawText(label, mx, my, labelPaint)
                continue
            }
    
            // Eraser — paint transparent along stroke path
            if (dp.shapeType == "eraser" && dp.points.size >= 2) {
                val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    strokeWidth = dp.strokeWidth
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                val eraserPath = Path()
                eraserPath.moveTo(dp.points[0].x, dp.points[0].y)
                for (i in 1 until dp.points.size) eraserPath.lineTo(dp.points[i].x, dp.points[i].y)
                canvas.drawPath(eraserPath, eraserPaint)
                continue
            }
    
            // Blur brush — Gaussian blur along the stroke path
            if (dp.shapeType == "blur" && dp.points.size >= 2) {
                val radius = (operationPath.strokeWidth * 2).toInt().coerceAtLeast(4)
                for (pt in operationPath.points) {
                    val cx = pt.x.toInt(); val cy = pt.y.toInt()
                    val half = radius
                    val l = (cx - half).coerceAtLeast(0)
                    val t = (cy - half).coerceAtLeast(0)
                    val r = (cx + half).coerceAtMost(result.width)
                    val b = (cy + half).coerceAtMost(result.height)
                    val w = r - l; val h = b - t
                    if (w < 3 || h < 3) continue
                    // Box blur by downscale + upscale
                    var region: Bitmap? = null; var tiny: Bitmap? = null; var blurred: Bitmap? = null
                    try {
                        region = Bitmap.createBitmap(result, l, t, w, h).copy(Bitmap.Config.ARGB_8888, false)
                        val scale = 4
                        tiny = Bitmap.createScaledBitmap(region, (w / scale).coerceAtLeast(1), (h / scale).coerceAtLeast(1), true)
                        blurred = Bitmap.createScaledBitmap(tiny, w, h, true)
                        canvas.drawBitmap(blurred, l.toFloat(), t.toFloat(), null)
                    } finally {
                        region?.recycle(); tiny?.recycle(); blurred?.recycle()
                    }
                }
                continue
            }
    
            // Emoji overlay
            if (dp.shapeType == "emoji" && dp.text != null && dp.points.isNotEmpty()) {
                val p = dp.points.first()
                val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = dp.strokeWidth * 5
                }
                val fm = emojiPaint.fontMetrics
                canvas.drawText(dp.text, p.x, p.y - (fm.ascent + fm.descent) / 2f, emojiPaint)
                continue
            }
    
            // Callout (numbered circle)
            if (dp.shapeType == "callout" && dp.text != null && dp.points.isNotEmpty()) {
                val p = dp.points.first()
                val radius = dp.strokeWidth * 2
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = dp.color; style = Paint.Style.FILL
                }
                canvas.drawCircle(p.x, p.y, radius, fillPaint)
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (dp.color == 0xFFFFFFFF.toInt() || dp.color == 0xFFFFFF00.toInt()) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                    textSize = radius * 1.2f
                    textAlign = Paint.Align.CENTER
                    style = Paint.Style.FILL
                }
                val fm = textPaint.fontMetrics
                canvas.drawText(dp.text, p.x, p.y - (fm.ascent + fm.descent) / 2f, textPaint)
                continue
            }
    
            // Neon glow pen
            if (dp.shapeType == "neon" && dp.points.size >= 2) {
                val neonPath = Path()
                neonPath.moveTo(dp.points[0].x, dp.points[0].y)
                for (i in 1 until dp.points.size) neonPath.lineTo(dp.points[i].x, dp.points[i].y)
                // Outer glow
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = dp.color; strokeWidth = dp.strokeWidth * 3; style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND; alpha = 80
                    maskFilter = android.graphics.BlurMaskFilter(dp.strokeWidth * 2, android.graphics.BlurMaskFilter.Blur.NORMAL)
                }
                canvas.drawPath(neonPath, glowPaint)
                // Mid layer
                val midPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = dp.color; strokeWidth = dp.strokeWidth; style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND; alpha = 200
                }
                canvas.drawPath(neonPath, midPaint)
                // Bright core
                val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFFFFFFF.toInt(); strokeWidth = dp.strokeWidth * 0.6f; style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawPath(neonPath, corePaint)
                continue
            }
    
            // Highlighter (semi-transparent wide stroke)
            if (dp.shapeType == "highlight" && dp.points.size >= 2) {
                paint.alpha = 100 // ~40% opacity
                val path = Path()
                path.moveTo(dp.points[0].x, dp.points[0].y)
                for (i in 1 until dp.points.size) path.lineTo(dp.points[i].x, dp.points[i].y)
                canvas.drawPath(path, paint)
                paint.alpha = 255
                continue
            }
    
            // Magnifier loupe — circular zoomed inset
            if (dp.shapeType == "magnifier" && dp.points.isNotEmpty()) {
                val p = dp.points.first()
                val loupeRadius = 120f // pixels in bitmap space
                val zoomFactor = 2f
                val loupeCx = p.x; val loupeCy = p.y - loupeRadius - 20f
    
                // Border
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFFFFFFF.toInt(); style = Paint.Style.FILL
                }
                canvas.drawCircle(loupeCx, loupeCy, loupeRadius + 4f, borderPaint)
    
                // Clip and draw zoomed content. Read from a snapshot — drawing `result` onto its
                // own backing canvas is undefined and yields a blank/corrupt loupe.
                val snapshot = result.copy(Bitmap.Config.ARGB_8888, false)
                canvas.save()
                val clipPath = Path()
                clipPath.addCircle(loupeCx, loupeCy, loupeRadius, Path.Direction.CW)
                canvas.clipPath(clipPath)
                canvas.translate(loupeCx - p.x * zoomFactor, loupeCy - p.y * zoomFactor)
                canvas.scale(zoomFactor, zoomFactor)
                canvas.drawBitmap(snapshot, 0f, 0f, null)
                canvas.restore()
                snapshot.recycle()
    
                // Ring border
                val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = dp.color; style = Paint.Style.STROKE; strokeWidth = 3f
                }
                canvas.drawCircle(loupeCx, loupeCy, loupeRadius, ringPaint)
    
                // Crosshair
                val chPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = dp.color; strokeWidth = 1.5f
                }
                canvas.drawLine(loupeCx - 15f, loupeCy, loupeCx + 15f, loupeCy, chPaint)
                canvas.drawLine(loupeCx, loupeCy - 15f, loupeCx, loupeCy + 15f, chPaint)
                continue
            }
    
            // Spotlight — dim entire image except the selected rectangle
            if (dp.shapeType == "spotlight" && dp.points.size >= 2) {
                val p1 = dp.points.first(); val p2 = dp.points.last()
                if (p1.x == p2.x && p1.y == p2.y) continue // Skip zero-size spotlight
                val sl = minOf(p1.x, p2.x); val st = minOf(p1.y, p2.y)
                val sr = maxOf(p1.x, p2.x); val sb = maxOf(p1.y, p2.y)
                val dimPaint = Paint().apply { color = 0x99000000.toInt(); style = Paint.Style.FILL }
                canvas.drawRect(0f, 0f, result.width.toFloat(), st, dimPaint)
                canvas.drawRect(0f, sb, result.width.toFloat(), result.height.toFloat(), dimPaint)
                canvas.drawRect(0f, st, sl, sb, dimPaint)
                canvas.drawRect(sr, st, result.width.toFloat(), sb, dimPaint)
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xCCFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f
                }
                canvas.drawRect(sl, st, sr, sb, borderPaint)
                continue
            }
    
            // Text
            if (dp.shapeType == "text" && dp.text != null && dp.points.isNotEmpty()) {
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = dp.color
                    textSize = dp.strokeWidth * 3
                    style = Paint.Style.FILL
                }
                val p = dp.points.first()
                if (dp.filled) {
                    val bounds = android.graphics.Rect()
                    textPaint.getTextBounds(dp.text, 0, dp.text.length, bounds)
                    val pad = textPaint.textSize * 0.3f
                    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC000000.toInt(); style = Paint.Style.FILL }
                    canvas.drawRoundRect(p.x - pad, p.y + bounds.top - pad, p.x + bounds.width() + pad,
                        p.y + bounds.bottom + pad, pad, pad, bgPaint)
                }
                canvas.drawText(dp.text, p.x, p.y, textPaint)
                continue
            }
    
            // Shape types
            if (dp.shapeType == "rect" && dp.points.size >= 2) {
                val p1 = dp.points.first(); val p2 = dp.points.last()
                val l = minOf(p1.x, p2.x); val t = minOf(p1.y, p2.y)
                val r = maxOf(p1.x, p2.x); val b = maxOf(p1.y, p2.y)
                if (dp.filled) { paint.style = Paint.Style.FILL; canvas.drawRect(l, t, r, b, paint); paint.style = Paint.Style.STROKE }
                else canvas.drawRect(l, t, r, b, paint)
                continue
            }
            if (dp.shapeType == "circle" && dp.points.size >= 2) {
                val p1 = dp.points.first(); val p2 = dp.points.last()
                val l = minOf(p1.x, p2.x); val t = minOf(p1.y, p2.y)
                val r = maxOf(p1.x, p2.x); val b = maxOf(p1.y, p2.y)
                if (dp.filled) { paint.style = Paint.Style.FILL; canvas.drawOval(l, t, r, b, paint); paint.style = Paint.Style.STROKE }
                else canvas.drawOval(l, t, r, b, paint)
                continue
            }
    
            // Flood fill — fill contiguous region at tap point with selected color
            if (dp.shapeType == "fill" && dp.points.isNotEmpty()) {
                val fx = operationPath.points[0].x.toInt().coerceIn(0, result.width - 1)
                val fy = operationPath.points[0].y.toInt().coerceIn(0, result.height - 1)
                floodFill(result, fx, fy, dp.color)
                continue
            }
    
            // Smart Erase — mask-based object removal with edge-aware inpainting.
            if ((dp.shapeType == "smart_erase" || dp.shapeType == "heal") && dp.points.size >= 2) {
                SmartEraseEngine.eraseInPlace(result, operationPath)
                continue
            }
    
            // Freehand or bezier path
            val path = Path()
            if (dp.controlPoint != null && dp.points.size >= 2) {
                path.moveTo(dp.points[0].x, dp.points[0].y)
                path.quadTo(dp.controlPoint.x, dp.controlPoint.y, dp.points.last().x, dp.points.last().y)
            } else {
                path.moveTo(dp.points[0].x, dp.points[0].y)
                for (i in 1 until dp.points.size) path.lineTo(dp.points[i].x, dp.points[i].y)
            }
            canvas.drawPath(path, paint)
    
            // Arrow head
            if (dp.isArrow && dp.points.size >= 2) {
                val last = dp.points.last()
                val prev = if (dp.controlPoint != null) {
                    val cp = dp.controlPoint; val t = 0.95f
                    PointF((1-t)*(1-t)*dp.points[0].x + 2*(1-t)*t*cp.x + t*t*last.x,
                           (1-t)*(1-t)*dp.points[0].y + 2*(1-t)*t*cp.y + t*t*last.y)
                } else dp.points[dp.points.size - 2]
                val dx = last.x - prev.x; val dy = last.y - prev.y
                val len = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                if (len > 0) {
                    val ux = dx / len; val uy = dy / len
                    val hl = dp.strokeWidth * 4; val hw = dp.strokeWidth * 2.5f
                    val arrowPath = Path()
                    arrowPath.moveTo(last.x, last.y)
                    arrowPath.lineTo(last.x - ux * hl + uy * hw, last.y - uy * hl - ux * hw)
                    arrowPath.moveTo(last.x, last.y)
                    arrowPath.lineTo(last.x - ux * hl - uy * hw, last.y - uy * hl + ux * hw)
                    canvas.drawPath(arrowPath, paint)
                }
            }
            } finally {
                if (layerSaveCount != -1) canvas.restoreToCount(layerSaveCount)
            }
        }
        return result
    }
    
    private fun floodFill(bitmap: Bitmap, x: Int, y: Int, fillColor: Int) {
        val w = bitmap.width; val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val targetColor = pixels[y * w + x]
        if (targetColor == fillColor) return
        val tolerance = 30 // color distance tolerance
        fun colorClose(c1: Int, c2: Int): Boolean {
            val dr = ((c1 shr 16) and 0xFF) - ((c2 shr 16) and 0xFF)
            val dg = ((c1 shr 8) and 0xFF) - ((c2 shr 8) and 0xFF)
            val db = (c1 and 0xFF) - (c2 and 0xFF)
            return dr * dr + dg * dg + db * db <= tolerance * tolerance * 3
        }
        val queue = ArrayDeque<Int>(1024)
        val visited = BooleanArray(w * h)
        queue.add(y * w + x)
        visited[y * w + x] = true
        var filled = 0
        val maxFill = w * h / 2 // safety limit
        while (queue.isNotEmpty() && filled < maxFill) {
            val idx = queue.removeFirst()
            pixels[idx] = fillColor
            filled++
            val cx = idx % w; val cy = idx / w
            // Explicit 4-neighbour checks — avoid allocating a List<Pair> per pixel in the hot loop.
            if (cx > 0) { val ni = idx - 1; if (!visited[ni] && colorClose(pixels[ni], targetColor)) { visited[ni] = true; queue.add(ni) } }
            if (cx < w - 1) { val ni = idx + 1; if (!visited[ni] && colorClose(pixels[ni], targetColor)) { visited[ni] = true; queue.add(ni) } }
            if (cy > 0) { val ni = idx - w; if (!visited[ni] && colorClose(pixels[ni], targetColor)) { visited[ni] = true; queue.add(ni) } }
            if (cy < h - 1) { val ni = idx + w; if (!visited[ni] && colorClose(pixels[ni], targetColor)) { visited[ni] = true; queue.add(ni) } }
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }
    
    private fun getFilterColorMatrix(filterIndex: Int): ColorMatrix? {
        return when (filterIndex) {
            1 -> ColorMatrix().apply { setSaturation(0f) } // Mono
            2 -> ColorMatrix().apply { // Sepia
                setSaturation(0f)
                postConcat(ColorMatrix(floatArrayOf(1f,0f,0f,0f,40f, 0f,1f,0f,0f,20f, 0f,0f,1f,0f,-10f, 0f,0f,0f,1f,0f)))
            }
            3 -> ColorMatrix(floatArrayOf(0.9f,0f,0f,0f,0f, 0f,0.95f,0f,0f,0f, 0f,0f,1.1f,0f,20f, 0f,0f,0f,1f,0f)) // Cool
            4 -> ColorMatrix(floatArrayOf(1.1f,0f,0f,0f,15f, 0f,1.05f,0f,0f,5f, 0f,0f,0.9f,0f,-10f, 0f,0f,0f,1f,0f)) // Warm
            5 -> ColorMatrix().apply { // Vivid
                setSaturation(1.5f)
                postConcat(ColorMatrix(floatArrayOf(1.1f,0f,0f,0f,10f, 0f,1.1f,0f,0f,10f, 0f,0f,1.1f,0f,10f, 0f,0f,0f,1f,0f)))
            }
            6 -> ColorMatrix().apply { // Muted
                setSaturation(0.4f)
                postConcat(ColorMatrix(floatArrayOf(1f,0f,0f,0f,15f, 0f,1f,0f,0f,15f, 0f,0f,1f,0f,15f, 0f,0f,0f,1f,0f)))
            }
            7 -> ColorMatrix().apply { // Vintage
                setSaturation(0.5f)
                postConcat(ColorMatrix(floatArrayOf(1.05f,0.05f,0f,0f,20f, 0f,1f,0.05f,0f,10f, 0f,0f,0.9f,0f,0f, 0f,0f,0f,1f,0f)))
            }
            8 -> ColorMatrix().apply { // Noir
                setSaturation(0f)
                postConcat(ColorMatrix(floatArrayOf(1.4f,0f,0f,0f,-30f, 0f,1.4f,0f,0f,-30f, 0f,0f,1.4f,0f,-30f, 0f,0f,0f,1f,0f)))
            }
            9 -> ColorMatrix().apply { // Fade
                setSaturation(0.6f)
                postConcat(ColorMatrix(floatArrayOf(1f,0f,0f,0f,30f, 0f,1f,0f,0f,30f, 0f,0f,1f,0f,30f, 0f,0f,0f,0.9f,0f)))
            }
            10 -> ColorMatrix(floatArrayOf(-1f,0f,0f,0f,255f, 0f,-1f,0f,0f,255f, 0f,0f,-1f,0f,255f, 0f,0f,0f,1f,0f)) // Invert
            11 -> ColorMatrix(floatArrayOf(1.438f,-0.062f,-0.062f,0f,0f, -0.122f,1.378f,-0.122f,0f,0f, -0.016f,-0.016f,1.483f,0f,0f, 0f,0f,0f,1f,0f)) // Polaroid
            12 -> ColorMatrix().apply { // Grain
                setSaturation(0.8f)
                postConcat(ColorMatrix(floatArrayOf(1.05f,0.02f,0f,0f,8f, 0f,1.02f,0f,0f,4f, 0f,0f,0.95f,0f,-5f, 0f,0f,0f,1f,0f)))
            }
            // 13-16: per-pixel filters handled in applyAdjustments
            else -> null
        }
    }
    
    private fun applyAdjustments(bitmap: Bitmap, adj: FloatArray): Bitmap {
        val brightness = adj[0]; val contrast = adj[1]; val saturation = adj[2]
        val warmth = if (adj.size > 4) adj[4] else 0f
        val vignetteAmt = if (adj.size > 5) adj[5] else 0f
        val filterIndex = if (adj.size > 6) adj[6].toInt() else 0
        val sharpenAmt = if (adj.size > 7) adj[7] else 0f
        val highlightsAmt = if (adj.size > 9) adj[9] else 0f
        val shadowsAmt = if (adj.size > 10) adj[10] else 0f
        val tiltShiftAmt = if (adj.size > 11) adj[11] else 0f
        val denoiseAmt = if (adj.size > 12) adj[12] else 0f
        val curveRAmt = if (adj.size > 14) adj[14] else 0f
        val curveGAmt = if (adj.size > 15) adj[15] else 0f
        val curveBAmt = if (adj.size > 16) adj[16] else 0f
        if (brightness == 0f && contrast == 1f && saturation == 1f && warmth == 0f && vignetteAmt == 0f && filterIndex == 0 && sharpenAmt == 0f && highlightsAmt == 0f && shadowsAmt == 0f && tiltShiftAmt == 0f && denoiseAmt == 0f && curveRAmt == 0f && curveGAmt == 0f && curveBAmt == 0f) return bitmap
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val cm = ColorMatrix()
        // Apply image filter first
        val filterMat = getFilterColorMatrix(filterIndex)
        if (filterMat != null) cm.postConcat(filterMat)
        if (saturation != 1f) { val sat = ColorMatrix(); sat.setSaturation(saturation); cm.postConcat(sat) }
        if (contrast != 1f) {
            val t = (1f - contrast) / 2f * 255f
            cm.postConcat(ColorMatrix(floatArrayOf(contrast, 0f, 0f, 0f, t, 0f, contrast, 0f, 0f, t, 0f, 0f, contrast, 0f, t, 0f, 0f, 0f, 1f, 0f)))
        }
        if (brightness != 0f) {
            cm.postConcat(ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, brightness, 0f, 1f, 0f, 0f, brightness, 0f, 0f, 1f, 0f, brightness, 0f, 0f, 0f, 1f, 0f)))
        }
        if (warmth != 0f) {
            cm.postConcat(ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, warmth, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, -warmth, 0f, 0f, 0f, 1f, 0f)))
        }
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        // Vignette: radial gradient overlay
        if (vignetteAmt > 0.01f) {
            val vigPaint = Paint().apply {
                shader = android.graphics.RadialGradient(
                    result.width / 2f, result.height / 2f,
                    maxOf(result.width, result.height) * 0.7f,
                    intArrayOf(0x00000000, (vignetteAmt * 200).toInt().coerceAtMost(200) shl 24),
                    floatArrayOf(0.4f, 1f),
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, result.width.toFloat(), result.height.toFloat(), vigPaint)
        }
        // Highlights/Shadows: per-pixel luminance-based adjustment
        if (highlightsAmt != 0f || shadowsAmt != 0f) {
            val w = result.width; val h = result.height
            val pixels = IntArray(w * h)
            result.getPixels(pixels, 0, w, 0, 0, w, h)
            for (i in pixels.indices) {
                val px = pixels[i]
                var r = (px shr 16) and 0xFF; var g = (px shr 8) and 0xFF; var b = px and 0xFF
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                // Highlights affect bright pixels (lum > 128), shadows affect dark pixels (lum < 128)
                val hiFactor = ((lum - 128f) / 128f).coerceIn(0f, 1f) // 0 for darks, 1 for brights
                val shFactor = ((128f - lum) / 128f).coerceIn(0f, 1f) // 1 for darks, 0 for brights
                val adj2 = highlightsAmt * hiFactor * 0.5f + shadowsAmt * shFactor * 0.5f
                r = (r + adj2).toInt().coerceIn(0, 255)
                g = (g + adj2).toInt().coerceIn(0, 255)
                b = (b + adj2).toInt().coerceIn(0, 255)
                pixels[i] = (px and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
            }
            result.setPixels(pixels, 0, w, 0, 0, w, h)
        }
        // Curves: per-channel gamma adjustment
        if (curveRAmt != 0f || curveGAmt != 0f || curveBAmt != 0f) {
            val w = result.width; val h = result.height
            val pixels = IntArray(w * h)
            result.getPixels(pixels, 0, w, 0, 0, w, h)
            // Build LUTs for each channel: gamma curve from -100..+100 mapped to gamma 0.5..2.0
            fun buildLut(amount: Float): IntArray {
                val lut = IntArray(256)
                if (amount == 0f) { for (i in 0..255) lut[i] = i; return lut }
                val gamma = if (amount > 0) 1f / (1f + amount / 50f) else 1f + (-amount / 50f)
                for (i in 0..255) {
                    lut[i] = (255.0 * Math.pow(i / 255.0, gamma.toDouble())).roundToInt().coerceIn(0, 255)
                }
                return lut
            }
            val lutR = buildLut(curveRAmt); val lutG = buildLut(curveGAmt); val lutB = buildLut(curveBAmt)
            for (i in pixels.indices) {
                val px = pixels[i]
                val r = lutR[(px shr 16) and 0xFF]
                val g = lutG[(px shr 8) and 0xFF]
                val b = lutB[px and 0xFF]
                pixels[i] = (px and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
            }
            result.setPixels(pixels, 0, w, 0, 0, w, h)
        }
        // Glitch effect (16): RGB channel shift
        if (filterIndex == 16) {
            val w = result.width; val h = result.height
            val pixels = IntArray(w * h)
            result.getPixels(pixels, 0, w, 0, 0, w, h)
            val out = IntArray(w * h)
            val shift = (w * 0.02f).toInt().coerceAtLeast(3) // 2% of width
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val idx = y * w + x
                    val rIdx = y * w + (x + shift).coerceAtMost(w - 1)
                    val bIdx = y * w + (x - shift).coerceAtLeast(0)
                    val r = (pixels[rIdx] shr 16) and 0xFF
                    val g = (pixels[idx] shr 8) and 0xFF
                    val b = pixels[bIdx] and 0xFF
                    out[idx] = (pixels[idx] and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
                }
            }
            result.setPixels(out, 0, w, 0, 0, w, h)
        }
        // Selective color pop filters (13=RedPop, 14=BluePop, 15=GreenPop)
        if (filterIndex in 13..15) {
            val w = result.width; val h = result.height
            val pixels = IntArray(w * h)
            result.getPixels(pixels, 0, w, 0, 0, w, h)
            val targetHue = when (filterIndex) { 13 -> 0f; 14 -> 220f; else -> 120f } // Red/Blue/Green
            val hueRange = 40f // degrees of hue to keep
            val hsv = FloatArray(3)
            for (i in pixels.indices) {
                val px = pixels[i]
                val r = (px shr 16) and 0xFF; val g = (px shr 8) and 0xFF; val b = px and 0xFF
                android.graphics.Color.RGBToHSV(r, g, b, hsv)
                val hueDiff = kotlin.math.abs(hsv[0] - targetHue).let { if (it > 180) 360 - it else it }
                if (hueDiff > hueRange || hsv[1] < 0.15f) {
                    // Desaturate — convert to grayscale
                    val gray = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
                    pixels[i] = (px and 0xFF000000.toInt()) or (gray shl 16) or (gray shl 8) or gray
                }
            }
            result.setPixels(pixels, 0, w, 0, 0, w, h)
        }
        // Noise reduction: blend with slightly blurred version
        if (denoiseAmt > 0.01f) {
            val w = result.width; val h = result.height
            val scale = (2 + denoiseAmt * 4).toInt().coerceIn(2, 6)
            val tiny = Bitmap.createScaledBitmap(result, (w / scale).coerceAtLeast(1), (h / scale).coerceAtLeast(1), true)
            val blurred = Bitmap.createScaledBitmap(tiny, w, h, true)
            tiny.recycle()
            val origPx = IntArray(w * h); val blurPx = IntArray(w * h)
            result.getPixels(origPx, 0, w, 0, 0, w, h)
            blurred.getPixels(blurPx, 0, w, 0, 0, w, h)
            val blend = denoiseAmt.coerceIn(0f, 0.8f) // never fully replace
            for (i in origPx.indices) {
                val o = origPx[i]; val b = blurPx[i]
                val r = ((o shr 16 and 0xFF) * (1 - blend) + (b shr 16 and 0xFF) * blend).toInt()
                val g = ((o shr 8 and 0xFF) * (1 - blend) + (b shr 8 and 0xFF) * blend).toInt()
                val bl = ((o and 0xFF) * (1 - blend) + (b and 0xFF) * blend).toInt()
                origPx[i] = (o and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or bl
            }
            result.setPixels(origPx, 0, w, 0, 0, w, h)
            blurred.recycle()
        }
        // Sharpen: 3x3 convolution kernel
        if (sharpenAmt > 0.01f) {
            val sharpened = applySharpen(result, sharpenAmt)
            if (sharpened !== result) result.recycle()
            val tiltResult = applyTiltShift(sharpened, tiltShiftAmt)
            if (tiltResult !== sharpened) sharpened.recycle()
            return tiltResult
        }
        val tiltResult = applyTiltShift(result, tiltShiftAmt)
        if (tiltResult !== result) result.recycle()
        return tiltResult
    }
    
    private fun applyTiltShift(bitmap: Bitmap, amount: Float): Bitmap {
        if (amount < 0.01f) return bitmap
        val w = bitmap.width; val h = bitmap.height
        if (w < 2 || h < 2) return bitmap
        // Create heavily blurred version via downscale/upscale
        val scale = (8 * amount).toInt().coerceIn(2, 16)
        val tiny = Bitmap.createScaledBitmap(bitmap, (w / scale).coerceAtLeast(1), (h / scale).coerceAtLeast(1), true)
        val blurred = Bitmap.createScaledBitmap(tiny, w, h, true)
        if (tiny !== blurred) tiny.recycle()
        // result is allocated empty — setPixels below overwrites every cell, so no bitmap.copy needed.
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val focusBand = 0.3f // 30% of height is the sharp center
        val focusTop = (h * (0.5f - focusBand / 2)).toInt()
        val focusBottom = (h * (0.5f + focusBand / 2)).toInt()
        val sharpPixels = IntArray(w * h)
        val blurPixels = IntArray(w * h)
        bitmap.getPixels(sharpPixels, 0, w, 0, 0, w, h)
        blurred.getPixels(blurPixels, 0, w, 0, 0, w, h)
        val outPixels = IntArray(w * h)
        for (y in 0 until h) {
            val blendFactor = when {
                y < focusTop -> 1f - (y.toFloat() / focusTop).coerceIn(0f, 1f) // top blur
                y > focusBottom -> ((y - focusBottom).toFloat() / (h - focusBottom)).coerceIn(0f, 1f) // bottom blur
                else -> 0f // center sharp
            }
            for (x in 0 until w) {
                val idx = y * w + x
                if (blendFactor < 0.01f) { outPixels[idx] = sharpPixels[idx]; continue }
                if (blendFactor > 0.99f) { outPixels[idx] = blurPixels[idx]; continue }
                val sp = sharpPixels[idx]; val bp = blurPixels[idx]
                val r = ((sp shr 16 and 0xFF) * (1 - blendFactor) + (bp shr 16 and 0xFF) * blendFactor).toInt()
                val g = ((sp shr 8 and 0xFF) * (1 - blendFactor) + (bp shr 8 and 0xFF) * blendFactor).toInt()
                val b = ((sp and 0xFF) * (1 - blendFactor) + (bp and 0xFF) * blendFactor).toInt()
                outPixels[idx] = (sp and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
            }
        }
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        blurred.recycle()
        return result
    }
    
    private fun applySharpen(bitmap: Bitmap, amount: Float): Bitmap {
        val w = bitmap.width; val h = bitmap.height
        // 3x3 unsharp-mask kernel needs at least 3px in each dimension; degenerate
        // bitmaps fall through unchanged instead of producing a one-pixel result.
        if (w < 3 || h < 3) return bitmap
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        // Unsharp mask: center = 1 + 4*amount, neighbors = -amount
        val center = 1f + 4f * amount
        val edge = -amount
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val c = pixels[idx]
                val t = pixels[(y - 1) * w + x]
                val b = pixels[(y + 1) * w + x]
                val l = pixels[y * w + (x - 1)]
                val r = pixels[y * w + (x + 1)]
                fun ch(px: Int, shift: Int) = (px shr shift) and 0xFF
                val nr = (ch(c, 16) * center + ch(t, 16) * edge + ch(b, 16) * edge + ch(l, 16) * edge + ch(r, 16) * edge).toInt().coerceIn(0, 255)
                val ng = (ch(c, 8) * center + ch(t, 8) * edge + ch(b, 8) * edge + ch(l, 8) * edge + ch(r, 8) * edge).toInt().coerceIn(0, 255)
                val nb = (ch(c, 0) * center + ch(t, 0) * edge + ch(b, 0) * edge + ch(l, 0) * edge + ch(r, 0) * edge).toInt().coerceIn(0, 255)
                out[idx] = (c and 0xFF000000.toInt()) or (nr shl 16) or (ng shl 8) or nb
            }
        }
        // Copy edges unchanged
        for (x in 0 until w) { out[x] = pixels[x]; out[(h - 1) * w + x] = pixels[(h - 1) * w + x] }
        for (y in 0 until h) { out[y * w] = pixels[y * w]; out[y * w + w - 1] = pixels[y * w + w - 1] }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }
    
    fun render(
        bitmap: Bitmap,
        rect: Rect,
        redactions: List<RedactionRegion>,
        drawPaths: List<DrawPath>,
        adj: FloatArray = floatArrayOf(0f, 1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        cutout: CutoutEditState = CutoutEditState(),
    ): Bitmap {
        // Redact source pixels before any geometric transform so rotate/perspective cannot move
        // secrets away from an untransformed mask. Invalid enabled regions abort the export.
        val redacted = applyRedactions(bitmap, redactions)
        // Apply free rotation before adjustments/crop. Straighten rotates within the original
        // editor coordinate frame (corners clipped) so the crop rect - expressed in source
        // coordinates - stays aligned with the preview instead of drifting onto an expanded canvas.
        val rotAngle = if (adj.size > 8) adj[8] else 0f
        require(cutout.bands.isEmpty() || rotAngle == 0f) { "Cut Out cannot be combined with free rotation" }
        val rotated = if (rotAngle != 0f) {
            createEditorSpaceStraightenedBitmap(redacted, rotAngle)
        } else redacted
    
        val adjusted = applyAdjustments(rotated, adj)
        preserveUltraHdrGainmap(rotated, adjusted)
        if (adjusted !== rotated && rotated !== bitmap) rotated.recycle()
        val drawn = applyDraw(adjusted, drawPaths)
        preserveUltraHdrGainmap(adjusted, drawn)
        if (drawn !== adjusted && adjusted !== bitmap) adjusted.recycle()
    
        // Perspective warp: adj[17..24] = quad TL.x, TL.y, TR.x, TR.y, BR.x, BR.y, BL.x, BL.y
        val hasPerspective = adj.size >= 25 && (adj[17] != 0f || adj[18] != 0f || adj[19] != 0f || adj[20] != 0f ||
                adj[21] != 0f || adj[22] != 0f || adj[23] != 0f || adj[24] != 0f)
        require(cutout.bands.isEmpty() || !hasPerspective) { "Cut Out cannot be combined with perspective" }
        if (hasPerspective) {
            val srcQuad = floatArrayOf(adj[17], adj[18], adj[19], adj[20], adj[21], adj[22], adj[23], adj[24])
            val topW = kotlin.math.sqrt(((srcQuad[2] - srcQuad[0]) * (srcQuad[2] - srcQuad[0]) + (srcQuad[3] - srcQuad[1]) * (srcQuad[3] - srcQuad[1])).toDouble())
            val botW = kotlin.math.sqrt(((srcQuad[4] - srcQuad[6]) * (srcQuad[4] - srcQuad[6]) + (srcQuad[5] - srcQuad[7]) * (srcQuad[5] - srcQuad[7])).toDouble())
            val leftH = kotlin.math.sqrt(((srcQuad[6] - srcQuad[0]) * (srcQuad[6] - srcQuad[0]) + (srcQuad[7] - srcQuad[1]) * (srcQuad[7] - srcQuad[1])).toDouble())
            val rightH = kotlin.math.sqrt(((srcQuad[4] - srcQuad[2]) * (srcQuad[4] - srcQuad[2]) + (srcQuad[5] - srcQuad[3]) * (srcQuad[5] - srcQuad[3])).toDouble())
            val outW = maxOf(topW, botW).toInt().coerceAtLeast(1)
            val outH = maxOf(leftH, rightH).toInt().coerceAtLeast(1)
            val dstQuad = floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat())
            val warpMatrix = Matrix()
            warpMatrix.setPolyToPoly(srcQuad, 0, dstQuad, 0, 4)
            val warped = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val warpCanvas = Canvas(warped)
            warpCanvas.drawBitmap(drawn, warpMatrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            preserveUltraHdrGainmap(drawn, warped, warpMatrix)
            if (drawn !== bitmap) drawn.recycle()
            return warped
        }
    
        val cl = rect.left.coerceIn(0, drawn.width - 1)
        val ct = rect.top.coerceIn(0, drawn.height - 1)
        val cw = rect.width().coerceAtMost(drawn.width - cl).coerceAtLeast(1)
        val ch = rect.height().coerceAtMost(drawn.height - ct).coerceAtLeast(1)
        val initialCrop = Bitmap.createBitmap(drawn, cl, ct, cw, ch)
        preserveUltraHdrGainmap(
            drawn,
            initialCrop,
            Matrix().apply { postTranslate(-cl.toFloat(), -ct.toFloat()) }
        )
        if (drawn !== bitmap && drawn !== initialCrop) drawn.recycle()
        val cropped = if (cutout.bands.isEmpty()) {
            initialCrop
        } else {
            val plan = CutoutSqueeze.createForCrop(
                bitmap.width,
                bitmap.height,
                cl,
                ct,
                cl + cw,
                ct + ch,
                cutout.bands,
                cutout.separatorStyle,
            )
            CutoutBitmapRenderer.render(initialCrop, plan).also { squeezed ->
                if (squeezed !== initialCrop) initialCrop.recycle()
            }
        }
    
        // Shape crop masking
        val shapeType = if (adj.size > 3) adj[3] else 0f
        val gradIdx = if (adj.size > 13) adj[13].toInt() else 0
        val shaped: Bitmap = if (shapeType == 1f) {
            // Circle
            val size = minOf(cropped.width, cropped.height)
            val s = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(s)
            val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            c.drawCircle(size / 2f, size / 2f, size / 2f, shapePaint)
            shapePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            c.drawBitmap(cropped, -(cropped.width - size) / 2f, -(cropped.height - size) / 2f, shapePaint)
            s
        } else if (shapeType == 2f) {
            // Rounded rect
            val s = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
            val c = Canvas(s)
            val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val radius = minOf(cropped.width, cropped.height) * 0.08f
            c.drawRoundRect(RectF(0f, 0f, cropped.width.toFloat(), cropped.height.toFloat()), radius, radius, shapePaint)
            shapePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            c.drawBitmap(cropped, 0f, 0f, shapePaint)
            s
        } else if (shapeType == 3f) {
            // Star (5-point)
            val size = minOf(cropped.width, cropped.height)
            val s = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(s)
            val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val starPath = Path()
            val cx = size / 2f; val cy = size / 2f; val outerR = size / 2f; val innerR = outerR * 0.38f
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) outerR else innerR
                val angle = Math.toRadians((i * 36.0 - 90.0))
                val x = cx + r * kotlin.math.cos(angle).toFloat()
                val y = cy + r * kotlin.math.sin(angle).toFloat()
                if (i == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
            }
            starPath.close()
            c.drawPath(starPath, shapePaint)
            shapePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            c.drawBitmap(cropped, -(cropped.width - size) / 2f, -(cropped.height - size) / 2f, shapePaint)
            s
        } else if (shapeType == 4f) {
            // Heart
            val size = minOf(cropped.width, cropped.height)
            val s = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(s)
            val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val heartPath = Path()
            val w = size.toFloat(); val h = size.toFloat()
            heartPath.moveTo(w / 2, h * 0.25f)
            heartPath.cubicTo(w * 0.15f, h * -0.05f, -w * 0.1f, h * 0.45f, w / 2, h * 0.95f)
            heartPath.lineTo(w / 2, h * 0.25f)
            heartPath.cubicTo(w * 0.85f, h * -0.05f, w * 1.1f, h * 0.45f, w / 2, h * 0.95f)
            heartPath.close()
            c.drawPath(heartPath, shapePaint)
            shapePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            c.drawBitmap(cropped, -(cropped.width - size) / 2f, -(cropped.height - size) / 2f, shapePaint)
            s
        } else if (shapeType == 5f) {
            // Triangle (equilateral)
            val size = minOf(cropped.width, cropped.height)
            val s = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(s)
            val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val triPath = Path()
            triPath.moveTo(size / 2f, size * 0.05f)
            triPath.lineTo(size * 0.95f, size * 0.95f)
            triPath.lineTo(size * 0.05f, size * 0.95f)
            triPath.close()
            c.drawPath(triPath, shapePaint)
            shapePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            c.drawBitmap(cropped, -(cropped.width - size) / 2f, -(cropped.height - size) / 2f, shapePaint)
            s
        } else if (shapeType == 6f) {
            // Hexagon
            val size = minOf(cropped.width, cropped.height)
            val s = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(s)
            val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val hexPath = Path()
            val cx = size / 2f; val cy = size / 2f; val r = size / 2f * 0.95f
            for (i in 0 until 6) {
                val angle = Math.toRadians((i * 60.0 - 30.0))
                val x = cx + r * kotlin.math.cos(angle).toFloat()
                val y = cy + r * kotlin.math.sin(angle).toFloat()
                if (i == 0) hexPath.moveTo(x, y) else hexPath.lineTo(x, y)
            }
            hexPath.close()
            c.drawPath(hexPath, shapePaint)
            shapePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            c.drawBitmap(cropped, -(cropped.width - size) / 2f, -(cropped.height - size) / 2f, shapePaint)
            s
        } else if (shapeType == 7f) {
            // Diamond (rotated square)
            val size = minOf(cropped.width, cropped.height)
            val s = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(s)
            val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val diaPath = Path()
            val half = size / 2f * 0.95f
            val cx = size / 2f; val cy = size / 2f
            diaPath.moveTo(cx, cy - half)
            diaPath.lineTo(cx + half, cy)
            diaPath.lineTo(cx, cy + half)
            diaPath.lineTo(cx - half, cy)
            diaPath.close()
            c.drawPath(diaPath, shapePaint)
            shapePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            c.drawBitmap(cropped, -(cropped.width - size) / 2f, -(cropped.height - size) / 2f, shapePaint)
            s
        } else {
            cropped
        }
    
        if (shaped !== cropped) {
            val shapeTransform = Matrix().apply {
                postTranslate(
                    -(cropped.width - shaped.width) / 2f,
                    -(cropped.height - shaped.height) / 2f
                )
            }
            preserveUltraHdrGainmap(cropped, shaped, shapeTransform)
            cropped.recycle()
        }
    
        // Gradient background fill for transparent areas (shape crops only)
        if (gradIdx > 0 && shapeType >= 1f) {
            return applyGradientBackground(shaped, gradIdx)
        }
    
        return shaped
    }
    
    private fun applyGradientBackground(bitmap: Bitmap, gradIdx: Int): Bitmap {
        val w = bitmap.width; val h = bitmap.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        // Draw gradient background
        val (startColor, endColor) = when (gradIdx) {
            1 -> 0xFFFF6B35.toInt() to 0xFFF7C948.toInt() // Sunset (orange->yellow)
            2 -> 0xFF0077B6.toInt() to 0xFF00B4D8.toInt() // Ocean (deep blue->cyan)
            3 -> 0xFF7B2FBE.toInt() to 0xFFE040FB.toInt() // Purple (purple->pink)
            4 -> 0xFF1A1A2E.toInt() to 0xFF16213E.toInt() // Dark (dark blue shades)
            5 -> 0xFF00B09B.toInt() to 0xFF96C93D.toInt() // Mint (teal->green)
            6 -> 0xFFFF416C.toInt() to 0xFFFF4B2B.toInt() // Fire (red->orange)
            else -> return bitmap
        }
        val gradPaint = Paint().apply {
            shader = android.graphics.LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
                startColor, endColor, android.graphics.Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), gradPaint)
        // Draw the shape-cropped image on top (transparent areas show gradient)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        preserveUltraHdrGainmap(bitmap, result)
        bitmap.recycle()
        return result
    }
    
}
