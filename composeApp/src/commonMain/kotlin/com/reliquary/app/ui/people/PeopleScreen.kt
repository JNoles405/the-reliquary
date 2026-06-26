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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import com.reliquary.app.domain.Person
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.Screen
import com.reliquary.app.ui.components.PillButton
import com.reliquary.app.ui.theme.ReliquaryMuted
import com.reliquary.app.ui.theme.ReliquarySurface

@Composable
fun PeopleScreen(container: AppContainer, navigator: Navigator) {
    val people by remember { container.repository.people() }.collectAsState(emptyList())
    var newName by remember { mutableStateOf("") }
    var newContact by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("People", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Spacer(Modifier.height(12.dp))

        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ReliquarySurface).padding(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Add person", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(newName, { newName = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name") })
                OutlinedTextField(newContact, { newContact = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Contact (optional)") })
                PillButton(
                    label = "Add",
                    icon = Icons.Filled.Add,
                    background = if (newName.isBlank()) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                    foreground = Color.Black,
                ) {
                    if (newName.isNotBlank()) {
                        val now = nowMillis()
                        container.repository.upsertPerson(
                            Person(id = newId(), name = newName.trim(), contact = newContact.trim().ifBlank { null }, updatedAt = now),
                        )
                        newName = ""; newContact = ""
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        if (people.isEmpty()) {
            Text("No people yet. Add someone, or they're created when you loan an item.", color = ReliquaryMuted)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(people, key = { it.id }) { person ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(ReliquarySurface)
                            .clickable { navigator.push(Screen.Person(person.id)) }.padding(14.dp),
                    ) {
                        Column {
                            Text(person.name, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                            person.contact?.let { Text(it, color = ReliquaryMuted, fontSize = 13.sp) }
                        }
                    }
                }
            }
        }
    }
}
