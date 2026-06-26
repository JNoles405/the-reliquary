package com.reliquary.app.ui.people

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.reliquary.app.ui.theme.ReliquarySurfaceVariant
import com.reliquary.app.util.formatDate

@Composable
fun PersonScreen(container: AppContainer, personId: String, navigator: Navigator) {
    val repo = container.repository
    val person = remember(personId) { repo.getPerson(personId) }
    val loans by remember(personId) { repo.loansForPerson(personId) }.collectAsState(emptyList())

    if (person == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Not found", color = ReliquaryMuted) }
        return
    }

    var name by remember { mutableStateOf(person.name) }
    var contact by remember { mutableStateOf(person.contact ?: "") }
    val active = loans.filter { it.isActive }
    val past = loans.filter { it.returnedAt != null }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(person.name, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name") })
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(contact, { contact = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Contact") })
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Save", null, ReliquaryTeal, Color.Black) {
                if (name.isNotBlank()) {
                    repo.upsertPerson(person.copy(name = name.trim(), contact = contact.trim().ifBlank { null }, updatedAt = nowMillis()))
                }
            }
            PillButton("Delete", null, ReliquarySurfaceVariant, MaterialTheme.colorScheme.onBackground) {
                repo.deletePerson(personId)
                navigator.pop()
            }
        }

        Spacer(Modifier.height(20.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (active.isNotEmpty()) {
                item { SectionHeader("Currently borrowing (${active.size})") }
                items(active, key = { it.id }) { loan -> LoanRow(container, loan, navigator, active = true) }
            }
            if (past.isNotEmpty()) {
                item { SectionHeader("History (${past.size})") }
                items(past, key = { it.id }) { loan -> LoanRow(container, loan, navigator, active = false) }
            }
            if (loans.isEmpty()) {
                item { Text("No loans recorded for ${person.name}.", color = ReliquaryMuted) }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun LoanRow(container: AppContainer, loan: Loan, navigator: Navigator, active: Boolean) {
    val item = container.repository.getItem(loan.itemId)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(ReliquarySurface)
            .clickable { item?.let { navigator.push(Screen.Detail(it.id)) } }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item?.title ?: "Unknown item", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
            val detail = if (active) {
                "Loaned ${formatDate(loan.loanedAt)}" + (loan.dueAt?.let { " · due ${formatDate(it)}" } ?: "")
            } else {
                "${formatDate(loan.loanedAt)} → ${loan.returnedAt?.let { formatDate(it) } ?: "?"}"
            }
            Text(detail, color = ReliquaryMuted, fontSize = 13.sp)
        }
        if (active) {
            PillButton("Return", null, ReliquaryTeal, Color.Black) { container.repository.markLoanReturned(loan.id) }
        }
    }
}
