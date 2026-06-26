package com.reliquary.app.ui.loans

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reliquary.app.data.nowMillis
import com.reliquary.app.di.AppContainer
import com.reliquary.app.domain.Loan
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.Screen
import com.reliquary.app.ui.components.PillButton
import com.reliquary.app.ui.theme.ReliquaryMuted
import com.reliquary.app.ui.theme.ReliquaryTeal
import com.reliquary.app.ui.theme.ReliquarySurface
import com.reliquary.app.util.DAY_MILLIS
import com.reliquary.app.util.formatDate

@Composable
fun LoansScreen(container: AppContainer, navigator: Navigator) {
    val loans by remember { container.repository.activeLoans() }.collectAsState(emptyList())
    val now = nowMillis()
    val sorted = remember(loans) { loans.sortedWith(compareBy(nullsLast()) { it.dueAt }) }
    val overdue = sorted.count { dayDelta(it, now) < 0 }
    val soon = sorted.count { val d = dayDelta(it, now); d in 0..3 }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("On Loan", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        if (sorted.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Nothing is currently on loan.", color = ReliquaryMuted)
            return@Column
        }
        if (overdue > 0 || soon > 0) {
            Spacer(Modifier.height(4.dp))
            val parts = buildList {
                if (overdue > 0) add("$overdue overdue")
                if (soon > 0) add("$soon due soon")
            }
            Text(parts.joinToString(" · "), color = if (overdue > 0) ReliquaryTeal else ReliquaryMuted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(sorted, key = { it.id }) { loan ->
                val item = container.repository.getItem(loan.itemId)
                val person = container.repository.getPerson(loan.personId)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(ReliquarySurface)
                        .clickable { item?.let { navigator.push(Screen.Detail(it.id)) } }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            item?.title ?: "Unknown item",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("Borrowed by ${person?.name ?: "someone"}", color = ReliquaryMuted, fontSize = 13.sp)
                        val (text, urgent) = dueLabel(loan, now)
                        Text(text, color = if (urgent) ReliquaryTeal else ReliquaryMuted, fontSize = 12.sp, fontWeight = if (urgent) FontWeight.Bold else FontWeight.Normal)
                    }
                    PillButton(
                        label = "Return",
                        icon = null,
                        background = ReliquaryTeal,
                        foreground = Color.Black,
                    ) { container.repository.markLoanReturned(loan.id) }
                }
            }
        }
    }
}

/** Whole-day difference between the due date and now (negative = overdue). */
private fun dayDelta(loan: Loan, now: Long): Int {
    val due = loan.dueAt ?: return Int.MAX_VALUE
    return ((due / DAY_MILLIS) - (now / DAY_MILLIS)).toInt()
}

private fun dueLabel(loan: Loan, now: Long): Pair<String, Boolean> {
    val due = loan.dueAt ?: return "No due date" to false
    val d = dayDelta(loan, now)
    return when {
        d < 0 -> "Overdue by ${-d} ${if (-d == 1) "day" else "days"} (due ${formatDate(due)})" to true
        d == 0 -> "Due today" to true
        d <= 3 -> "Due in $d ${if (d == 1) "day" else "days"} (${formatDate(due)})" to true
        else -> "Due ${formatDate(due)}" to false
    }
}
