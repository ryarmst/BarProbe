package dev.barcodeworkbench.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The seven Material icons this app uses, vendored.
 *
 * `androidx.compose.material:material-icons-extended` was the obvious way to get these,
 * but it is frozen: its last release was 1.7.8 in February 2025, and the Compose BOM
 * still pins it there while material3 has moved on to 1.4.0. It also carries roughly
 * four thousand icons to supply seven, and it is the only route by which
 * `material-icons-core` reaches the build, so taking it out removes both.
 *
 * The path data below was extracted from that library rather than retyped, and
 * `WorkbenchIconsTest` compares every icon here against the original — the library is
 * kept as a test-only dependency purely so that comparison can run. If a vendored icon
 * ever drifts from the real one, the test fails rather than the icon quietly changing
 * shape.
 */
object WorkbenchIcons {

    val QrCode2: ImageVector by lazy {
        materialIcon("Outlined.QrCode2") {
            materialPath {
                moveTo(15.0f, 21.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(21.0f)
                close()
                moveTo(13.0f, 14.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(5.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(14.0f)
                close()
                moveTo(21.0f, 12.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(4.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(12.0f)
                close()
                moveTo(19.0f, 10.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(10.0f)
                close()
                moveTo(7.0f, 12.0f)
                horizontalLineTo(5.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(12.0f)
                close()
                moveTo(5.0f, 10.0f)
                horizontalLineTo(3.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(10.0f)
                close()
                moveTo(12.0f, 5.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(3.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineTo(5.0f)
                close()
                moveTo(4.5f, 4.5f)
                verticalLineToRelative(3.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(-3.0f)
                horizontalLineTo(4.5f)
                close()
                moveTo(9.0f, 9.0f)
                horizontalLineTo(3.0f)
                verticalLineTo(3.0f)
                horizontalLineToRelative(6.0f)
                verticalLineTo(9.0f)
                close()
                moveTo(4.5f, 16.5f)
                verticalLineToRelative(3.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(-3.0f)
                horizontalLineTo(4.5f)
                close()
                moveTo(9.0f, 21.0f)
                horizontalLineTo(3.0f)
                verticalLineToRelative(-6.0f)
                horizontalLineToRelative(6.0f)
                verticalLineTo(21.0f)
                close()
                moveTo(16.5f, 4.5f)
                verticalLineToRelative(3.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(-3.0f)
                horizontalLineTo(16.5f)
                close()
                moveTo(21.0f, 9.0f)
                horizontalLineToRelative(-6.0f)
                verticalLineTo(3.0f)
                horizontalLineToRelative(6.0f)
                verticalLineTo(9.0f)
                close()
                moveTo(19.0f, 19.0f)
                verticalLineToRelative(-3.0f)
                lineToRelative(-4.0f, 0.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(3.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineTo(19.0f)
                close()
                moveTo(17.0f, 12.0f)
                lineToRelative(-4.0f, 0.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(4.0f)
                verticalLineTo(12.0f)
                close()
                moveTo(13.0f, 10.0f)
                horizontalLineTo(7.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(10.0f)
                close()
                moveTo(14.0f, 9.0f)
                verticalLineTo(7.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineTo(5.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(4.0f)
                lineTo(14.0f, 9.0f)
                close()
                moveTo(6.75f, 5.25f)
                horizontalLineToRelative(-1.5f)
                verticalLineToRelative(1.5f)
                horizontalLineToRelative(1.5f)
                verticalLineTo(5.25f)
                close()
                moveTo(6.75f, 17.25f)
                horizontalLineToRelative(-1.5f)
                verticalLineToRelative(1.5f)
                horizontalLineToRelative(1.5f)
                verticalLineTo(17.25f)
                close()
                moveTo(18.75f, 5.25f)
                horizontalLineToRelative(-1.5f)
                verticalLineToRelative(1.5f)
                horizontalLineToRelative(1.5f)
                verticalLineTo(5.25f)
                close()
            }
        }
    }

    val CameraAlt: ImageVector by lazy {
        materialIcon("Outlined.CameraAlt") {
            materialPath {
                moveTo(20.0f, 4.0f)
                horizontalLineToRelative(-3.17f)
                lineTo(15.0f, 2.0f)
                lineTo(9.0f, 2.0f)
                lineTo(7.17f, 4.0f)
                lineTo(4.0f, 4.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(22.0f, 6.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(20.0f, 18.0f)
                lineTo(4.0f, 18.0f)
                lineTo(4.0f, 6.0f)
                horizontalLineToRelative(4.05f)
                lineToRelative(1.83f, -2.0f)
                horizontalLineToRelative(4.24f)
                lineToRelative(1.83f, 2.0f)
                lineTo(20.0f, 6.0f)
                verticalLineToRelative(12.0f)
                close()
                moveTo(12.0f, 7.0f)
                curveToRelative(-2.76f, 0.0f, -5.0f, 2.24f, -5.0f, 5.0f)
                reflectiveCurveToRelative(2.24f, 5.0f, 5.0f, 5.0f)
                reflectiveCurveToRelative(5.0f, -2.24f, 5.0f, -5.0f)
                reflectiveCurveToRelative(-2.24f, -5.0f, -5.0f, -5.0f)
                close()
                moveTo(12.0f, 15.0f)
                curveToRelative(-1.65f, 0.0f, -3.0f, -1.35f, -3.0f, -3.0f)
                reflectiveCurveToRelative(1.35f, -3.0f, 3.0f, -3.0f)
                reflectiveCurveToRelative(3.0f, 1.35f, 3.0f, 3.0f)
                reflectiveCurveToRelative(-1.35f, 3.0f, -3.0f, 3.0f)
                close()
            }
        }
    }

    val Inventory2: ImageVector by lazy {
        materialIcon("Outlined.Inventory2") {
            materialPath {
                moveTo(20.0f, 2.0f)
                horizontalLineTo(4.0f)
                curveTo(3.0f, 2.0f, 2.0f, 2.9f, 2.0f, 4.0f)
                verticalLineToRelative(3.01f)
                curveTo(2.0f, 7.73f, 2.43f, 8.35f, 3.0f, 8.7f)
                verticalLineTo(20.0f)
                curveToRelative(0.0f, 1.1f, 1.1f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(0.9f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(8.7f)
                curveToRelative(0.57f, -0.35f, 1.0f, -0.97f, 1.0f, -1.69f)
                verticalLineTo(4.0f)
                curveTo(22.0f, 2.9f, 21.0f, 2.0f, 20.0f, 2.0f)
                close()
                moveTo(19.0f, 20.0f)
                horizontalLineTo(5.0f)
                verticalLineTo(9.0f)
                horizontalLineToRelative(14.0f)
                verticalLineTo(20.0f)
                close()
                moveTo(20.0f, 7.0f)
                horizontalLineTo(4.0f)
                verticalLineTo(4.0f)
                horizontalLineToRelative(16.0f)
                verticalLineTo(7.0f)
                close()
            }
            materialPath {
                moveTo(9.0f, 12.0f)
                horizontalLineToRelative(6.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(-6.0f)
                close()
            }
        }
    }

    val Build: ImageVector by lazy {
        materialIcon("Outlined.Build") {
            materialPath {
                moveTo(22.61f, 18.99f)
                lineToRelative(-9.08f, -9.08f)
                curveToRelative(0.93f, -2.34f, 0.45f, -5.1f, -1.44f, -7.0f)
                curveTo(9.79f, 0.61f, 6.21f, 0.4f, 3.66f, 2.26f)
                lineTo(7.5f, 6.11f)
                lineTo(6.08f, 7.52f)
                lineTo(2.25f, 3.69f)
                curveTo(0.39f, 6.23f, 0.6f, 9.82f, 2.9f, 12.11f)
                curveToRelative(1.86f, 1.86f, 4.57f, 2.35f, 6.89f, 1.48f)
                lineToRelative(9.11f, 9.11f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
                lineToRelative(2.3f, -2.3f)
                curveToRelative(0.4f, -0.38f, 0.4f, -1.01f, 0.0f, -1.41f)
                close()
                moveTo(19.61f, 20.59f)
                lineToRelative(-9.46f, -9.46f)
                curveToRelative(-0.61f, 0.45f, -1.29f, 0.72f, -2.0f, 0.82f)
                curveToRelative(-1.36f, 0.2f, -2.79f, -0.21f, -3.83f, -1.25f)
                curveTo(3.37f, 9.76f, 2.93f, 8.5f, 3.0f, 7.26f)
                lineToRelative(3.09f, 3.09f)
                lineToRelative(4.24f, -4.24f)
                lineToRelative(-3.09f, -3.09f)
                curveToRelative(1.24f, -0.07f, 2.49f, 0.37f, 3.44f, 1.31f)
                curveToRelative(1.08f, 1.08f, 1.49f, 2.57f, 1.24f, 3.96f)
                curveToRelative(-0.12f, 0.71f, -0.42f, 1.37f, -0.88f, 1.96f)
                lineToRelative(9.45f, 9.45f)
                lineToRelative(-0.88f, 0.89f)
                close()
            }
        }
    }

    val MenuBook: ImageVector by lazy {
        materialIcon("AutoMirrored.Outlined.MenuBook", autoMirror = true) {
            materialPath {
                moveTo(21.0f, 5.0f)
                curveToRelative(-1.11f, -0.35f, -2.33f, -0.5f, -3.5f, -0.5f)
                curveToRelative(-1.95f, 0.0f, -4.05f, 0.4f, -5.5f, 1.5f)
                curveToRelative(-1.45f, -1.1f, -3.55f, -1.5f, -5.5f, -1.5f)
                reflectiveCurveTo(2.45f, 4.9f, 1.0f, 6.0f)
                verticalLineToRelative(14.65f)
                curveToRelative(0.0f, 0.25f, 0.25f, 0.5f, 0.5f, 0.5f)
                curveToRelative(0.1f, 0.0f, 0.15f, -0.05f, 0.25f, -0.05f)
                curveTo(3.1f, 20.45f, 5.05f, 20.0f, 6.5f, 20.0f)
                curveToRelative(1.95f, 0.0f, 4.05f, 0.4f, 5.5f, 1.5f)
                curveToRelative(1.35f, -0.85f, 3.8f, -1.5f, 5.5f, -1.5f)
                curveToRelative(1.65f, 0.0f, 3.35f, 0.3f, 4.75f, 1.05f)
                curveToRelative(0.1f, 0.05f, 0.15f, 0.05f, 0.25f, 0.05f)
                curveToRelative(0.25f, 0.0f, 0.5f, -0.25f, 0.5f, -0.5f)
                verticalLineTo(6.0f)
                curveTo(22.4f, 5.55f, 21.75f, 5.25f, 21.0f, 5.0f)
                close()
                moveTo(21.0f, 18.5f)
                curveToRelative(-1.1f, -0.35f, -2.3f, -0.5f, -3.5f, -0.5f)
                curveToRelative(-1.7f, 0.0f, -4.15f, 0.65f, -5.5f, 1.5f)
                verticalLineTo(8.0f)
                curveToRelative(1.35f, -0.85f, 3.8f, -1.5f, 5.5f, -1.5f)
                curveToRelative(1.2f, 0.0f, 2.4f, 0.15f, 3.5f, 0.5f)
                verticalLineTo(18.5f)
                close()
            }
            materialPath {
                moveTo(17.5f, 10.5f)
                curveToRelative(0.88f, 0.0f, 1.73f, 0.09f, 2.5f, 0.26f)
                verticalLineTo(9.24f)
                curveTo(19.21f, 9.09f, 18.36f, 9.0f, 17.5f, 9.0f)
                curveToRelative(-1.7f, 0.0f, -3.24f, 0.29f, -4.5f, 0.83f)
                verticalLineToRelative(1.66f)
                curveTo(14.13f, 10.85f, 15.7f, 10.5f, 17.5f, 10.5f)
                close()
            }
            materialPath {
                moveTo(13.0f, 12.49f)
                verticalLineToRelative(1.66f)
                curveToRelative(1.13f, -0.64f, 2.7f, -0.99f, 4.5f, -0.99f)
                curveToRelative(0.88f, 0.0f, 1.73f, 0.09f, 2.5f, 0.26f)
                verticalLineTo(11.9f)
                curveToRelative(-0.79f, -0.15f, -1.64f, -0.24f, -2.5f, -0.24f)
                curveTo(15.8f, 11.66f, 14.26f, 11.96f, 13.0f, 12.49f)
                close()
            }
            materialPath {
                moveTo(17.5f, 14.33f)
                curveToRelative(-1.7f, 0.0f, -3.24f, 0.29f, -4.5f, 0.83f)
                verticalLineToRelative(1.66f)
                curveToRelative(1.13f, -0.64f, 2.7f, -0.99f, 4.5f, -0.99f)
                curveToRelative(0.88f, 0.0f, 1.73f, 0.09f, 2.5f, 0.26f)
                verticalLineToRelative(-1.52f)
                curveTo(19.21f, 14.41f, 18.36f, 14.33f, 17.5f, 14.33f)
                close()
            }
        }
    }

    val Close: ImageVector by lazy {
        materialIcon("Filled.Close") {
            materialPath {
                moveTo(19.0f, 6.41f)
                lineTo(17.59f, 5.0f)
                lineTo(12.0f, 10.59f)
                lineTo(6.41f, 5.0f)
                lineTo(5.0f, 6.41f)
                lineTo(10.59f, 12.0f)
                lineTo(5.0f, 17.59f)
                lineTo(6.41f, 19.0f)
                lineTo(12.0f, 13.41f)
                lineTo(17.59f, 19.0f)
                lineTo(19.0f, 17.59f)
                lineTo(13.41f, 12.0f)
                close()
            }
        }
    }

    val Refresh: ImageVector by lazy {
        materialIcon("Filled.Refresh") {
            materialPath {
                moveTo(17.65f, 6.35f)
                curveTo(16.2f, 4.9f, 14.21f, 4.0f, 12.0f, 4.0f)
                curveToRelative(-4.42f, 0.0f, -7.99f, 3.58f, -7.99f, 8.0f)
                reflectiveCurveToRelative(3.57f, 8.0f, 7.99f, 8.0f)
                curveToRelative(3.73f, 0.0f, 6.84f, -2.55f, 7.73f, -6.0f)
                horizontalLineToRelative(-2.08f)
                curveToRelative(-0.82f, 2.33f, -3.04f, 4.0f, -5.65f, 4.0f)
                curveToRelative(-3.31f, 0.0f, -6.0f, -2.69f, -6.0f, -6.0f)
                reflectiveCurveToRelative(2.69f, -6.0f, 6.0f, -6.0f)
                curveToRelative(1.66f, 0.0f, 3.14f, 0.69f, 4.22f, 1.78f)
                lineTo(13.0f, 11.0f)
                horizontalLineToRelative(7.0f)
                verticalLineTo(4.0f)
                lineToRelative(-2.35f, 2.35f)
                close()
            }
        }
    }

    val Shuffle: ImageVector by lazy {
        materialIcon("Filled.Shuffle") {
            materialPath {
                moveTo(10.59f, 9.17f)
                lineTo(5.41f, 4.0f)
                lineTo(4.0f, 5.41f)
                lineToRelative(5.17f, 5.17f)
                lineToRelative(1.42f, -1.41f)
                close()
                moveTo(14.5f, 4.0f)
                lineToRelative(2.04f, 2.04f)
                lineTo(4.0f, 18.59f)
                lineTo(5.41f, 20.0f)
                lineTo(17.96f, 7.46f)
                lineTo(20.0f, 9.5f)
                lineTo(20.0f, 4.0f)
                horizontalLineToRelative(-5.5f)
                close()
                moveTo(14.83f, 13.41f)
                lineToRelative(-1.41f, 1.41f)
                lineToRelative(3.13f, 3.13f)
                lineTo(14.5f, 20.0f)
                lineTo(20.0f, 20.0f)
                verticalLineToRelative(-5.5f)
                lineToRelative(-2.04f, 2.04f)
                lineToRelative(-3.13f, -3.13f)
                close()
            }
        }
    }
}

/**
 * Mirrors the library's own `materialIcon` builder, including its defaults, so the
 * vendored icons compare equal to the originals rather than merely looking alike.
 */
private fun materialIcon(
    name: String,
    autoMirror: Boolean = false,
    block: ImageVector.Builder.() -> ImageVector.Builder,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.0.dp,
    defaultHeight = 24.0.dp,
    viewportWidth = 24.0f,
    viewportHeight = 24.0f,
    autoMirror = autoMirror,
).block().build()

/** Mirrors the library's `materialPath`: black solid fill, no stroke, non-zero fill. */
private fun ImageVector.Builder.materialPath(
    fillAlpha: Float = 1f,
    strokeAlpha: Float = 1f,
    pathFillType: PathFillType = PathFillType.NonZero,
    pathBuilder: PathBuilder.() -> Unit,
) = path(
    fill = SolidColor(Color.Black),
    fillAlpha = fillAlpha,
    stroke = null,
    strokeAlpha = strokeAlpha,
    strokeLineWidth = 1f,
    strokeLineCap = StrokeCap.Butt,
    strokeLineJoin = StrokeJoin.Bevel,
    strokeLineMiter = 1f,
    pathFillType = pathFillType,
    pathBuilder = pathBuilder,
)
