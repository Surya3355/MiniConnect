package com.surya.miniconnect.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Static, non-interactive mock of the RiderConnect ride screen so the call
 * bubble sits in a realistic context. Nothing here reacts to input.
 */
@Composable
fun RideMapBackdrop(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        MapCanvas(modifier = Modifier.fillMaxSize())
        RiderMarkers()
        TopChips(modifier = Modifier.align(Alignment.TopStart))
        SideControls(modifier = Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun MapCanvas(modifier: Modifier) {
    val land = Color(0xFFF2F0EA)
    val park = Color(0xFFD8E8C6)
    val water = Color(0xFFAECBEA)
    val road = Color(0xFFFFFFFF)
    val roadEdge = Color(0xFFE2DED4)
    val route = Color(0xFFEA6A1E)

    Canvas(modifier = modifier.background(land)) {
        val w = size.width
        val h = size.height

        // Green park blocks
        drawRect(park, topLeft = Offset(0f, h * 0.62f), size = androidx.compose.ui.geometry.Size(w * 0.28f, h * 0.22f))
        drawRect(park, topLeft = Offset(w * 0.72f, h * 0.10f), size = androidx.compose.ui.geometry.Size(w * 0.28f, h * 0.16f))

        // River band (diagonal)
        val river = Path().apply {
            moveTo(w * 0.62f, 0f)
            lineTo(w * 0.78f, 0f)
            lineTo(w * 0.60f, h)
            lineTo(w * 0.44f, h)
            close()
        }
        drawPath(river, water)

        // Road grid
        val stroke = 14f
        val edge = 20f
        listOf(0.2f, 0.45f, 0.7f).forEach { fx ->
            drawLine(roadEdge, Offset(w * fx, 0f), Offset(w * fx, h), edge)
            drawLine(road, Offset(w * fx, 0f), Offset(w * fx, h), stroke)
        }
        listOf(0.25f, 0.5f, 0.78f).forEach { fy ->
            drawLine(roadEdge, Offset(0f, h * fy), Offset(w, h * fy), edge)
            drawLine(road, Offset(0f, h * fy), Offset(w, h * fy), stroke)
        }

        // The highlighted route (thick orange line with a gentle bend)
        val routePath = Path().apply {
            moveTo(w * 0.42f, h)
            cubicTo(w * 0.36f, h * 0.72f, w * 0.55f, h * 0.55f, w * 0.52f, h * 0.38f)
            cubicTo(w * 0.50f, h * 0.24f, w * 0.62f, h * 0.16f, w * 0.66f, 0f)
        }
        drawPath(routePath, route.copy(alpha = 0.25f), style = Stroke(width = 34f, cap = StrokeCap.Round))
        drawPath(routePath, route, style = Stroke(width = 16f, cap = StrokeCap.Round))
    }
}

@Composable
private fun RiderMarkers() {
    Box(modifier = Modifier.fillMaxSize()) {
        // Other riders
        MarkerPill(
            text = "MP",
            container = Color(0xFF334155),
            content = Color.White,
            modifier = Modifier.align(Alignment.TopCenter).offset(x = 70.dp, y = 150.dp)
        )
        RiderChip(
            initials = "SK",
            meta = "72",
            modifier = Modifier.align(Alignment.Center).offset(x = 20.dp, y = (-60).dp)
        )
        RiderChip(
            initials = "RV",
            meta = "2 min ago",
            faded = true,
            modifier = Modifier.align(Alignment.BottomCenter).offset(x = (-40).dp, y = (-140).dp)
        )

        // YOU marker (orange, with heading)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center).offset(x = (-10).dp, y = 40.dp)
        ) {
            Surface(shape = CircleShape, color = Color(0xFFEA6A1E), shadowElevation = 4.dp) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Filled.Navigation, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Text("YOU", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun MarkerPill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(shape = CircleShape, color = container, shadowElevation = 3.dp, modifier = modifier) {
        Text(
            text = text,
            color = content,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun RiderChip(initials: String, meta: String, faded: Boolean = false, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = if (faded) 0.6f else 1f),
        shadowElevation = 3.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier.size(22.dp).background(Color(0xFF64748B), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(initials, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text(meta, color = Color(0xFF334155), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun TopChips(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 2.dp) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.size(9.dp).background(Color(0xFF16A34A), CircleShape))
                Text("Online", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF1E293B))
                Text("6 riders", color = Color(0xFF64748B), fontSize = 13.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InfoPill("↑ 2 ahead", Color(0xFFEA6A1E))
            InfoPill("↕ 4 here", Color(0xFF16A34A))
            InfoPill("↓ 1 behind", Color(0xFF64748B))
        }
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.92f), shadowElevation = 1.dp) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.LightMode, contentDescription = null, tint = Color(0xFFEA6A1E), modifier = Modifier.size(14.dp))
                Text("Auto theme — bright sunlight detected", fontSize = 11.sp, color = Color(0xFF475569))
            }
        }
    }
}

@Composable
private fun InfoPill(text: String, tint: Color) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White, shadowElevation = 1.dp) {
        Text(
            text = text,
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun SideControls(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(end = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ControlButton(Icons.Filled.GpsFixed, Color(0xFF1E293B), Color.White)
        ControlButton(Icons.Filled.CenterFocusStrong, Color.White, Color(0xFF1E293B))
        Spacer(modifier = Modifier.height(4.dp))
        ControlButton(Icons.Filled.LightMode, Color(0xFFEA6A1E), Color.White)
        ControlButton(Icons.Filled.DarkMode, Color.White, Color(0xFF94A3B8))
        Text("auto", fontSize = 10.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ControlButton(icon: androidx.compose.ui.graphics.vector.ImageVector, container: Color, content: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = container, shadowElevation = 3.dp, modifier = Modifier.size(44.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        }
    }
}
