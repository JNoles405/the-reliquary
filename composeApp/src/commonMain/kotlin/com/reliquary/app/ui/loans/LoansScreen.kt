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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.reliquary.app.sync.defaultSyncFilePath
import com.reliquary.app.sync.writeTextFile
import com.reliquary.app.util.isDesktopPlatform
import com.reliquary.app.util.openUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import com.reliquary.app.ui.components.VScrollbar
import com.reliquary.app.ui.theme.ReliquaryMuted
import com.reliquary.app.ui.theme.ReliquarySurface
import com.reliquary.app.util.DAY_MILLIS
import com.reliquary.app.util.formatDate

@Composable
fun LoansScreen(container: AppContainer, navigator: Navigator) {
    val loans by remember { container.repository.activeLoans() }.collectAsState(emptyList())
    val now = nowMillis()
    val scope = rememberCoroutineScope()
    val sorted = remember(loans) { loans.sortedWith(compareBy(nullsLast()) { it.dueAt }) }
    val overdue = sorted.count { dayDelta(it, now) < 0 }
    val soon = sorted.count { val d = dayDelta(it, now); d in 0..3 }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("On Loan", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 24.sp, modifier = Modifier.weight(1f))
            if (sorted.any { it.dueAt != null }) {
                PillButton("Export .ics", null, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onBackground) {
                    scope.launch {
                        val ics = withContext(Dispatchers.Default) { buildLoansIcs(container, sorted) }
                        val path = defaultSyncFilePath().replace("reliquary-sync.json", "reliquary-loans.ics")
                        withContext(Dispatchers.Default) { writeTextFile(path, ics) }
                        if (isDesktopPlatform()) openUrl("file:///" + path.replace("\\", "/"))
                    }
                }
                Spacer(Modifier.width(10.dp))
            }
            PillButton("History", null, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onBackground) { navigator.push(Screen.LoanHistory) }
            Spacer(Modifier.width(10.dp))
            PillButton("People", null, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onBackground) { navigator.push(Screen.People) }
        }
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
            Text(parts.joinToString(" · "), color = if (overdue > 0) MaterialTheme.colorScheme.primary else ReliquaryMuted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
        val listState = rememberLazyListState()
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val sections = listOf(
                "Overdue" to sorted.filter { dayDelta(it, now) < 0 },
                "Due this week" to sorted.filter { dayDelta(it, now) in 0..7 },
                "Later" to sorted.filter { it.dueAt != null && dayDelta(it, now) > 7 },
                "No due date" to sorted.filter { it.dueAt == null },
            )
            sections.forEach { (title, group) ->
                if (group.isNotEmpty()) {
                    item(key = "header-$title") { LoanSectionHeader(title, group.size) }
                    items(group, key = { it.id }) { loan -> LoanRow(container, loan, now, navigator) }
                }
            }
        }
            VScrollbar(listState)
        }
    }
}

@Composable
fun LoanHistoryScreen(container: AppContainer, navigator: Navigator) {
    val history = remember { container.repository.loanHistoryNow() }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Loan History", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "Every item that has been lent out, past and present.",
            color = ReliquaryMuted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))
        if (history.isEmpty()) {
            Text("Nothing has been loaned out yet.", color = ReliquaryMuted)
            return@Column
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val listState = rememberLazyListState()
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(history, key = { it.id }) { loan ->
                    val item = container.repository.getItem(loan.itemId)
                    val person = container.repository.getPerson(loan.personId)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface)
                            .clickable { item?.let { navigator.push(Screen.Detail(it.id)) } }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item?.title ?: "Unknown item", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                            Text("Lent to ${person?.name ?: "someone"}", color = ReliquaryMuted, fontSize = 13.sp)
                            val out = formatDate(loan.loanedAt)
                            val back = loan.returnedAt?.let { "returned ${formatDate(it)}" } ?: "still out"
                            Text("Out $out · $back", color = ReliquaryMuted, fontSize = 12.sp)
                        }
                        val (label, color) = if (loan.returnedAt == null) {
                            "On loan" to MaterialTheme.colorScheme.primary
                        } else {
                            "Returned" to ReliquaryMuted
                        }
                        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            VScrollbar(listState)
        }
    }
}

@Composable
private fun LoanSectionHeader(title: String, count: Int) {
    Text(
        "$title ($count)",
        color = if (title == "Overdue") MaterialTheme.colorScheme.primary else ReliquaryMuted,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun LoanRow(container: AppContainer, loan: Loan, now: Long, navigator: Navigator) {
    val item = container.repository.getItem(loan.itemId)
    val person = container.repository.getPerson(loan.personId)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface)
            .clickable { item?.let { navigator.push(Screen.Detail(it.id)) } }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item?.title ?: "Unknown item", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
            Text("Borrowed by ${person?.name ?: "someone"}", color = ReliquaryMuted, fontSize = 13.sp)
            val (text, urgent) = dueLabel(loan, now)
            Text(text, color = if (urgent) MaterialTheme.colorScheme.primary else ReliquaryMuted, fontSize = 12.sp, fontWeight = if (urgent) FontWeight.Bold else FontWeight.Normal)
        }
        PillButton(
            label = "Return",
            icon = null,
            background = MaterialTheme.colorScheme.primary,
            foreground = Color.Black,
        ) { container.repository.markLoanReturned(loan.id) }
    }
}

/** A .ics calendar (all-day VEVENTs) of loan due dates for subscribing in a calendar app. */
private fun buildLoansIcs(container: AppContainer, loans: List<Loan>): String {
    val sb = StringBuilder()
    sb.append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//The Reliquary//EN\r\nCALSCALE:GREGORIAN\r\n")
    val stamp = formatDate(nowMillis()).replace("-", "") + "T000000Z"
    loans.filter { it.dueAt != null }.forEach { loan ->
        val item = container.repository.getItem(loan.itemId)
        val person = container.repository.getPerson(loan.personId)
        val due = formatDate(loan.dueAt!!).replace("-", "")
        val summary = icsEscape((item?.title ?: "Item") + " due back" + (person?.name?.let { " from $it" } ?: ""))
        sb.append("BEGIN:VEVENT\r\n")
        sb.append("UID:loan-${loan.id}@reliquary\r\n")
        sb.append("DTSTAMP:$stamp\r\n")
        sb.append("DTSTART;VALUE=DATE:$due\r\n")
        sb.append("SUMMARY:$summary\r\n")
        sb.append("END:VEVENT\r\n")
    }
    sb.append("END:VCALENDAR\r\n")
    return sb.toString()
}

private fun icsEscape(s: String): String =
    s.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", " ")

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
