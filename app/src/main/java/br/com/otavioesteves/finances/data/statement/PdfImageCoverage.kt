package br.com.otavioesteves.finances.data.statement

import android.graphics.Path
import android.graphics.PointF
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage
import kotlin.math.abs

/** Uses drawn area, rather than image resolution, so a bank logo does not trigger OCR. */
internal class PdfImageCoverage(page: PDPage) : PDFGraphicsStreamEngine(page) {
    private var imageArea = 0f
    private var point = PointF()
    fun fraction(): Float {
        processPage(page)
        return (imageArea / (page.cropBox.width * page.cropBox.height)).coerceIn(0f, 1f)
    }
    override fun drawImage(image: PDImage) {
        val m = graphicsState.currentTransformationMatrix
        imageArea += abs(m.scaleX * m.scaleY - m.shearX * m.shearY)
    }
    override fun appendRectangle(a: PointF, b: PointF, c: PointF, d: PointF) { point = a }
    override fun moveTo(x: Float, y: Float) { point = PointF(x, y) }
    override fun lineTo(x: Float, y: Float) { point = PointF(x, y) }
    override fun curveTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) { point = PointF(x3, y3) }
    override fun getCurrentPoint(): PointF = point
    override fun clip(windingRule: Path.FillType) = Unit
    override fun closePath() = Unit
    override fun endPath() = Unit
    override fun strokePath() = Unit
    override fun fillPath(windingRule: Path.FillType) = Unit
    override fun fillAndStrokePath(windingRule: Path.FillType) = Unit
    override fun shadingFill(shadingName: COSName) = Unit
}
