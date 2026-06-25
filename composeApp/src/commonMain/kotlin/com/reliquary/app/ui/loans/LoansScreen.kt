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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reliquary.app.di.AppContainer
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.Screen
import com.reliquary.app.ui.components.PillButton
import com.reliquary.app.ui.theme.ReliquaryMuted
import com.reliquary.app.ui.theme.ReliquaryRed
import com.reliquary.app.ui.theme.ReliquarySurface

@Composable
fun LoansScreen(container: AppContainer, navigator: Navigator) {
    val loans by remember { container.repository.activeLoans() }.collectAsState(emptyList())

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(
            "On Loan",
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
        )
        Spacer(Modifier.height(12.dp))
        if (loans.isEmpty()) {
            Text("Nothing is currently on loan.", color = ReliquaryMuted)
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(loans, key = { it.id }) { loan ->
                val item = container.repository.getItem(loan.itemId)
                val person = container.repository.getPerson(loan.personId)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(ReliquarySurface)
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
                    }
                    PillButton(
                        label = "Return",
                        icon = null,
                        background = ReliquaryRed,
                        foreground = MaterialTheme.colorScheme.onBackground,
                    ) { container.repository.markLoanReturned(loan.id) }
                }
            }
        }
    }
}
