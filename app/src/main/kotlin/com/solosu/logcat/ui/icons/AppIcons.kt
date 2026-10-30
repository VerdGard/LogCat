package com.solosu.logcat.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 自绘矢量图标(24x24 线性风格)。
 * 不使用 material-icons-extended,避免为几个图标引入数 MB 资源。
 * 为规避不同 Compose 版本对弧线 API 的差异,这里只用直线段与多边形。
 */
object AppIcons {

    val Logs: ImageVector by lazy {
        stroke("Logs") {
            moveTo(4f, 6f); lineTo(20f, 6f)
            moveTo(4f, 11f); lineTo(15f, 11f)
            moveTo(4f, 16f); lineTo(18f, 16f)
            moveTo(4f, 21f); lineTo(12f, 21f)
        }
    }

    val Crash: ImageVector by lazy {
        stroke("Crash") {
            moveTo(12f, 3.5f); lineTo(22f, 20.5f); lineTo(2f, 20.5f); close()
            moveTo(12f, 9f); lineTo(12f, 14.5f)
            moveTo(12f, 17.5f); lineTo(12f, 18f)
        }
    }

    val Settings: ImageVector by lazy {
        stroke("Settings") {
            moveTo(3f, 7f); lineTo(21f, 7f)
            moveTo(3f, 12f); lineTo(21f, 12f)
            moveTo(3f, 17f); lineTo(21f, 17f)
            moveTo(9f, 5f); lineTo(9f, 9f)
            moveTo(15f, 10f); lineTo(15f, 14f)
            moveTo(8f, 15f); lineTo(8f, 19f)
        }
    }

    val Search: ImageVector by lazy {
        stroke("Search") {
            // 八边形近似圆形放大镜
            moveTo(10.5f, 4f); lineTo(14.1f, 5.6f); lineTo(16.5f, 8.5f); lineTo(17.2f, 12.1f)
            lineTo(15.9f, 15.6f); lineTo(13f, 18f); lineTo(9.4f, 18.6f); lineTo(6f, 17f)
            lineTo(4.2f, 14f); lineTo(4f, 10.4f); lineTo(5.8f, 7f); lineTo(8.6f, 4.7f); close()
            moveTo(14.6f, 14.6f); lineTo(20f, 20f)
        }
    }

    val Play: ImageVector by lazy {
        solid("Play") { moveTo(7f, 4.5f); lineTo(19.5f, 12f); lineTo(7f, 19.5f); close() }
    }

    val Pause: ImageVector by lazy {
        stroke("Pause") {
            moveTo(9f, 5f); lineTo(9f, 19f)
            moveTo(15f, 5f); lineTo(15f, 19f)
        }
    }

    val Clear: ImageVector by lazy {
        stroke("Clear") {
            moveTo(12f, 4f); lineTo(12f, 20f)
            moveTo(4f, 12f); lineTo(20f, 12f)
            moveTo(6.5f, 6.5f); lineTo(17.5f, 17.5f)
            moveTo(17.5f, 6.5f); lineTo(6.5f, 17.5f)
        }
    }

    val Copy: ImageVector by lazy {
        stroke("Copy") {
            moveTo(9f, 9f); lineTo(20f, 9f); lineTo(20f, 20f); lineTo(9f, 20f); close()
            moveTo(15f, 9f); lineTo(15f, 4f); lineTo(4f, 4f); lineTo(4f, 15f); lineTo(9f, 15f)
        }
    }

    val Share: ImageVector by lazy {
        stroke("Share") {
            moveTo(12f, 15f); lineTo(12f, 3.5f)
            moveTo(7.5f, 8f); lineTo(12f, 3.5f); lineTo(16.5f, 8f)
            moveTo(4.5f, 13f); lineTo(4.5f, 20.5f); lineTo(19.5f, 20.5f); lineTo(19.5f, 13f)
        }
    }

    val Delete: ImageVector by lazy {
        stroke("Delete") {
            moveTo(4f, 7f); lineTo(20f, 7f)
            moveTo(9f, 7f); lineTo(9f, 3.5f); lineTo(15f, 3.5f); lineTo(15f, 7f)
            moveTo(6f, 7f); lineTo(7.2f, 20.5f); lineTo(16.8f, 20.5f); lineTo(18f, 7f)
            moveTo(10.5f, 11f); lineTo(10.5f, 16.5f)
            moveTo(13.5f, 11f); lineTo(13.5f, 16.5f)
        }
    }

    val Refresh: ImageVector by lazy {
        stroke("Refresh") {
            // 环形箭头:八边形截断 + 箭头
            moveTo(19.5f, 12f)
            lineTo(17.5f, 7.2f); lineTo(12.8f, 4.6f); lineTo(7.6f, 5.4f)
            lineTo(4.2f, 8.8f); lineTo(3.6f, 13.4f); lineTo(6.2f, 17.4f)
            lineTo(10.6f, 19.4f); lineTo(15.2f, 18.4f)
            moveTo(19.5f, 12f); lineTo(19.8f, 6.6f)
            moveTo(19.5f, 12f); lineTo(14.4f, 12.6f)
        }
    }

    val Export: ImageVector by lazy {
        stroke("Export") {
            moveTo(12f, 3f); lineTo(12f, 15f)
            moveTo(7.5f, 7.5f); lineTo(12f, 3f); lineTo(16.5f, 7.5f)
            moveTo(4f, 14f); lineTo(4f, 21f); lineTo(20f, 21f); lineTo(20f, 14f)
        }
    }

    val ChevronRight: ImageVector by lazy {
        stroke("ChevronRight") { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
    }

    val ArrowBack: ImageVector by lazy {
        stroke("ArrowBack") { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) }
    }

    val Folder: ImageVector by lazy {
        stroke("Folder") {
            moveTo(3f, 6f); lineTo(10f, 6f); lineTo(12f, 8.5f); lineTo(21f, 8.5f)
            lineTo(21f, 19.5f); lineTo(3f, 19.5f); close()
        }
    }

    val Info: ImageVector by lazy {
        stroke("Info") {
            moveTo(12f, 3.5f); lineTo(20.5f, 12f); lineTo(12f, 20.5f); lineTo(3.5f, 12f); close()
            moveTo(12f, 11f); lineTo(12f, 16.5f)
            moveTo(12f, 8f); lineTo(12f, 8.5f)
        }
    }

    val Theme: ImageVector by lazy {
        stroke("Theme") {
            moveTo(12f, 3f); lineTo(20f, 9f); lineTo(20f, 19f); lineTo(4f, 19f); lineTo(4f, 9f); close()
            moveTo(9f, 19f); lineTo(9f, 12f); lineTo(15f, 12f); lineTo(15f, 19f)
        }
    }

    val Link: ImageVector by lazy {
        stroke("Link") {
            moveTo(10f, 13f); lineTo(14f, 9f)
            moveTo(8.5f, 9.5f); lineTo(6.5f, 11.5f); lineTo(6.5f, 11.5f)
            moveTo(8f, 16f); lineTo(6f, 18f); lineTo(6f, 12f)
            moveTo(16f, 8f); lineTo(18f, 6f); lineTo(18f, 12f)
            moveTo(9f, 15f); lineTo(15f, 9f); lineTo(15f, 15f); lineTo(9f, 15f); close()
        }
    }

    val Wrap: ImageVector by lazy {
        stroke("Wrap") {
            moveTo(3f, 6f); lineTo(21f, 6f)
            moveTo(3f, 12f); lineTo(15f, 12f); lineTo(18f, 12f)
            moveTo(18f, 12f); lineTo(18f, 16f); lineTo(15f, 16f)
            moveTo(3f, 18f); lineTo(12f, 18f)
        }
    }

    val ArrowDown: ImageVector by lazy {
        stroke("ArrowDown") {
            moveTo(12f, 4f); lineTo(12f, 20f)
            moveTo(6f, 14f); lineTo(12f, 20f); lineTo(18f, 14f)
        }
    }

    val Filter: ImageVector by lazy {
        stroke("Filter") {
            moveTo(3f, 5f); lineTo(21f, 5f); lineTo(14f, 13f); lineTo(14f, 20f); lineTo(10f, 18f); lineTo(10f, 13f); close()
        }
    }

    val Expand: ImageVector by lazy {
        stroke("Expand") {
            moveTo(4f, 9f); lineTo(12f, 3f); lineTo(20f, 9f)
            moveTo(4f, 15f); lineTo(12f, 21f); lineTo(20f, 15f)
        }
    }

    private fun stroke(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    private fun solid(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                stroke = null,
                pathBuilder = block,
            )
        }.build()
}
