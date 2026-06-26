package com.reliquary.app.data

import com.reliquary.app.db.Custom_tab as DbCustomTab
import com.reliquary.app.db.Item as DbItem
import com.reliquary.app.db.Loan as DbLoan
import com.reliquary.app.db.Person as DbPerson
import com.reliquary.app.domain.CollectionItem
import com.reliquary.app.domain.CustomTab
import com.reliquary.app.domain.Loan
import com.reliquary.app.domain.Person

internal fun Boolean.toDbLong(): Long = if (this) 1L else 0L

internal fun DbItem.toDomain(): CollectionItem = CollectionItem(
    id = id,
    mediaType = media_type,
    customTabId = custom_tab_id,
    title = title,
    sortTitle = sort_title,
    subtitle = subtitle,
    creators = creators,
    releaseYear = release_year,
    description = description,
    coverPath = cover_path,
    coverUrl = cover_url,
    barcode = barcode,
    identifierType = identifier_type,
    identifier = identifier,
    rating = rating,
    genres = genres,
    format = format,
    location = location,
    extraJson = extra_json,
    notes = notes,
    status = status,
    wanted = wanted != 0L,
    tags = tags,
    favorite = favorite != 0L,
    addedAt = added_at,
    updatedAt = updated_at,
    deleted = deleted != 0L,
)

internal fun DbPerson.toDomain(): Person = Person(
    id = id,
    name = name,
    contact = contact,
    notes = notes,
    updatedAt = updated_at,
    deleted = deleted != 0L,
)

internal fun DbLoan.toDomain(): Loan = Loan(
    id = id,
    itemId = item_id,
    personId = person_id,
    loanedAt = loaned_at,
    dueAt = due_at,
    returnedAt = returned_at,
    notes = notes,
    updatedAt = updated_at,
    deleted = deleted != 0L,
)

internal fun DbCustomTab.toDomain(): CustomTab = CustomTab(
    id = id,
    name = name,
    icon = icon,
    position = position,
    supportsBarcode = supports_barcode != 0L,
    updatedAt = updated_at,
    deleted = deleted != 0L,
)
