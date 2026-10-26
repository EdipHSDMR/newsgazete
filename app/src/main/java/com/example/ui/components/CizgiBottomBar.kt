package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import com.example.ui.theme.LocalReducedMotion
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CizgiColor

enum class CizgiScreen(val route: String, val title: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    AKIS("akis", "Akış", Icons.AutoMirrored.Filled.Article, Icons.AutoMirrored.Outlined.Article),
    KESFET("kesfet", "Keşfet", Icons.Filled.Explore, Icons.Outlined.Explore),
    SPOR("spor", "Spor", Icons.Filled.SportsSoccer, Icons.Outlined.SportsSoccer),
    KAYDEDILENLER("kaydedilenler", "Kitaplık", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder),
    PIYASA("piyasa", "Piyasa", Icons.Filled.BarChart, Icons.Outlined.BarChart),
}

@Composable
fun CizgiBottomBar(currentScreen: CizgiScreen, onNavigate: (CizgiScreen) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(64.dp).shadow(18.dp, RoundedCornerShape(25.dp))
                .clip(RoundedCornerShape(25.dp)).background(CizgiColor.NavyDark).testTag("cizgi_bottom_bar"),
        ) {
            val cell = maxWidth / CizgiScreen.entries.size
            val offset by animateDpAsState(cell * currentScreen.ordinal + 5.dp, if (LocalReducedMotion.current) snap() else spring(dampingRatio = 0.8f, stiffness = 420f), label = "navigation")
            Box(Modifier.offset(x = offset, y = 6.dp).width(cell - 10.dp).height(52.dp).clip(RoundedCornerShape(19.dp)).background(CizgiColor.Blue))
            Row(Modifier.fillMaxSize()) {
                CizgiScreen.entries.forEach { screen ->
                    val active = screen == currentScreen
                    val color by animateColorAsState(if (active) Color.White else Color(0xFFBCC7DA), animationSpec = if (LocalReducedMotion.current) snap() else tween(180), label = "tab_color")
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(22.dp))
                            .clickable(role = Role.Tab) { onNavigate(screen) }
                            .semantics { selected = active }.testTag("nav_item_" + screen.route),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(if (active) screen.selectedIcon else screen.unselectedIcon, contentDescription = null, tint = color, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(screen.title, color = color, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                    }
                }
            }
        }
    }
}
