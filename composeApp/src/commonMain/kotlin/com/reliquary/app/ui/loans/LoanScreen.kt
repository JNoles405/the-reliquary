package com.reliquary.app.ui.loans

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reliquary.app.data.newId
import com.reliquary.app.data.nowMillis
import com.reliquary.app.di.AppContainer
import com.reliquary.app.domain.Loan
import com.reliquary.app.domain.Person
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.components.PillButton
import com.reliquary.app.ui.components.VScrollColumn
import com.reliquary.app.ui.theme.ReliquaryMuted
import com.reliquary.app.ui.theme.ReliquarySurface
import com.reliquary.app.ui.theme.ReliquarySurfaceVariant
import com.reliquary.app.util.DAY_MILLIS
import com.reliquary.app.util.formatDate

@Composable
fun LoanScreen(container: AppContainer, itemId: String, navigator: Navigator) {
    val repo = container.repository
    val item = remember(itemId) { repo.getItem(itemId) }
    val loans by remember(itemId) { repo.loansForItem(itemId) }.collectAsState(emptyList())
    val people by remember { repo.people() }.collectAsState(emptyList())
    val activeLoan = loans.firstOrNull { it.isActive }

    VScrollColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = item?.title ?: "Loan",
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
        )

        if (activeLoan != null) {
            ActiveLoanCard(container, activeLoan) {
                repo.markLoanReturned(activeLoan.id)
                navigator.pop()
            }
        } else {
            NewLoanForm(people = people) { name, days ->
                val now = nowMillis()
                val person = people.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    ?: Person(id = newId(), name = name, updatedAt = now).also { repo.upsertPerson(it) }
                repo.upsertLoan(
                    Loan(
                        id = newId(),
                        itemId = itemId,
                        personId = person.id,
                        loanedAt = now,
                        dueAt = if (days > 0) now + days * DAY_MILLIS else null,
                        updatedAt = now,
                    ),
                )
                navigator.pop()
            }
        }
    }
}

@Composable
private fun ActiveLoanCard(container: AppContainer, loan: Loan, onReturn: () -> Unit) {
    val borrower = remember(loan.personId) { container.repository.getPerson(loan.personId) }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ReliquarySurface).padding(16.dp)) {
        Column {
            Text("Currently on loan", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Borrower: ${borrower?.name ?: "Unknown"}", color = MaterialTheme.colorScheme.onBackground)
            Text("Loaned: ${formatDate(loan.loanedAt)}", color = ReliquaryMuted, fontSize = 13.sp)
            loan.dueAt?.let {
                val overdue = loan.isOverdue(nowMillis())
                Text(
                    "Due: ${formatDate(it)}${if (overdue) "  (overdue)" else ""}",
                    color = if (overdue) MaterialTheme.colorScheme.primary else ReliquaryMuted,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(14.dp))
            PillButton(
                label = "Mark returned",
                icon = null,
                background = MaterialTheme.colorScheme.primary,
                foreground = Color.White,
                onClick = onReturn,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewLoanForm(people: List<Person>, onLoan: (name: String, days: Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(14) }

    Text("Loan this out", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Borrower name") },
    )

    if (people.isNotEmpty()) {
        Text("Recent people", color = ReliquaryMuted, fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            people.take(12).forEach { person ->
                Chip(person.name, selected = person.name.equals(name, true)) { name = person.name }
            }
        }
    }

    Text("Borrow for", color = ReliquaryMuted, fontSize = 12.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(7 to "1 week", 14 to "2 weeks", 30 to "1 month", 0 to "No due date").forEach { (d, label) ->
            Chip(label, selected = days == d) { days = d }
        }
    }

    Spacer(Modifier.height(4.dp))
    val dueText = if (days > 0) "Due ${formatDate(nowMillis() + days * DAY_MILLIS)}" else "No due date"
    Text(dueText, color = ReliquaryMuted, fontSize = 13.sp)

    PillButton(
        label = "Loan out",
        icon = null,
        background = if (name.isBlank()) ReliquarySurfaceVariant else MaterialTheme.colorScheme.primary,
        foreground = Color.White,
    ) { if (name.isNotBlank()) onLoan(name.trim(), days) }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else ReliquarySurfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
