package team.bjtuss.bjtuselfservice.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.reminder.ClassroomReservation
import team.bjtuss.bjtuselfservice.reminder.ClassroomReservationScheduler
import team.bjtuss.bjtuselfservice.repository.DataStoreRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassroomReservationsScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reservations by DataStoreRepository.getClassroomReservations().collectAsState(emptyList())
    val sorted = reservations.sortedWith(compareBy({ it.date }, { it.slots.minOrNull() ?: 0 }))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("教室预约", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (sorted.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Default.EventAvailable,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "还没有教室预约",
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "进入教室详情可以预约日期和节次",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(sorted, key = { it.id }) { reservation ->
                    ReservationCard(
                        reservation = reservation,
                        onOpen = {
                            navController.navigate(
                                "ClassroomDetection/" + android.net.Uri.encode(reservation.building) +
                                    "?room=" + android.net.Uri.encode(reservation.room)
                            )
                        },
                        onCancel = {
                            scope.launch {
                                DataStoreRepository.removeClassroomReservation(reservation.id)
                                ClassroomReservationScheduler.cancel(context, reservation)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReservationCard(
    reservation: ClassroomReservation,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
) {
    val dateText = runCatching {
        LocalDate.parse(reservation.date).format(DateTimeFormatter.ofPattern("MM月dd日"))
    }.getOrDefault(reservation.date)
    val periods = reservation.slots.sorted().joinToString("、") { "第" + (it + 1) + "节" }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reservation.room,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = reservation.building,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = periods,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onOpen, modifier = Modifier.weight(1f)) {
                    Text("去教室")
                }
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("取消预约")
                }
            }
        }
    }
}
