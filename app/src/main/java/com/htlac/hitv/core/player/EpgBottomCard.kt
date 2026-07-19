package com.htlac.hitv.core.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.htlac.hitv.core.data.local.EpgProgram
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EpgBottomCard(channelName: String, currentProgram: EpgProgram?, nextProgram: EpgProgram?) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF1C1C1E).copy(alpha = 0.85f), RoundedCornerShape(16.dp)).padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = channelName, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.3f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.width(32.dp))
        Column(modifier = Modifier.weight(0.7f)) {
            if (currentProgram != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("正在播放", color = Color(0xFF0A84FF), fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                    Text(text = "${timeFormatter.format(Date(currentProgram.startTime))} - ${timeFormatter.format(Date(currentProgram.endTime))}", color = Color.LightGray, fontSize = 16.sp, modifier = Modifier.width(130.dp))
                    Text(currentProgram.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text("暂无当前节目信息", color = Color.Gray, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (nextProgram != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("即将播放", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.width(80.dp))
                    Text(text = "${timeFormatter.format(Date(nextProgram.startTime))} - ${timeFormatter.format(Date(nextProgram.endTime))}", color = Color.DarkGray, fontSize = 14.sp, modifier = Modifier.width(130.dp))
                    Text(nextProgram.title, color = Color.LightGray, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}