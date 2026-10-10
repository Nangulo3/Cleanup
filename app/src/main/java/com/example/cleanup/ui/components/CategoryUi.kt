package com.example.cleanup.ui.components

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.cleanup.R
import com.example.cleanup.domain.model.Category

/** Texto e icono de cada categoría; se mantienen fuera de domain/ para no mezclar UI y reglas. */
@StringRes
fun Category.titleRes(): Int = when (this) {
    Category.DUPLICATES -> R.string.category_duplicates
    Category.SIMILAR_PHOTOS -> R.string.category_similar_photos
    Category.SIMILAR_SCREENSHOTS -> R.string.category_similar_screenshots
    Category.SIMILAR_VIDEOS -> R.string.category_similar_videos
    Category.OTHER_SCREENSHOTS -> R.string.category_other_screenshots
    Category.PHOTOS -> R.string.category_photos
    Category.VIDEOS -> R.string.category_videos
    Category.CHAT_PHOTOS -> R.string.category_chat_photos
}

fun Category.icon(): ImageVector = when (this) {
    Category.DUPLICATES -> AppIcons.Copy
    Category.SIMILAR_PHOTOS -> AppIcons.PhotoLibrary
    Category.SIMILAR_SCREENSHOTS -> AppIcons.Screenshot
    Category.SIMILAR_VIDEOS -> AppIcons.VideoLibrary
    Category.OTHER_SCREENSHOTS -> AppIcons.Screenshot
    Category.PHOTOS -> AppIcons.Photo
    Category.VIDEOS -> AppIcons.Video
    Category.CHAT_PHOTOS -> AppIcons.Chat
}
