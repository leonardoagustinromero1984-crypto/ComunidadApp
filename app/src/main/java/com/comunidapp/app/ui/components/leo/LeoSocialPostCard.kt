package com.comunidapp.app.ui.components.leo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.PostType
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.theme.BrandCream
import com.comunidapp.app.ui.theme.BrandGreen
import com.comunidapp.app.ui.theme.BrandGreenContainer
import com.comunidapp.app.ui.theme.BrandOrange
import com.comunidapp.app.ui.theme.BrandOrangeContainer
import com.comunidapp.app.ui.theme.BrandOrangeSoft
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandWhite
import com.comunidapp.app.ui.theme.ComunidappTheme
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.MutedText
import com.comunidapp.app.ui.theme.NeutralBorder
import com.comunidapp.app.ui.theme.UrgentContainer
import com.comunidapp.app.ui.theme.UrgentRed
import com.comunidapp.app.ui.util.displayDate

@Composable
fun LeoSocialPostCard(
    post: FeedPost,
    modifier: Modifier = Modifier,
    isLiked: Boolean = false,
    isSaved: Boolean = false,
    petName: String? = null,
    onAuthorClick: ((String) -> Unit)? = null,
    onLikeClick: (() -> Unit)? = null,
    onCommentClick: (() -> Unit)? = null,
    onShareClick: (() -> Unit)? = null,
    onSaveClick: (() -> Unit)? = null,
    onReportClick: (() -> Unit)? = null,
    onBlockClick: (() -> Unit)? = null,
    onSpecialCta: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val special = specialBadgeFor(post.type)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = BrandWhite,
        shape = RoundedCornerShape(LeoDimens.RadiusCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, NeutralBorder),
        shadowElevation = 0.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceCompact),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(BrandOrangeContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (post.authorImageUrl != null) {
                        PetImage(
                            imageUrl = post.authorImageUrl,
                            modifier = Modifier.size(40.dp),
                            cornerRadius = 20.dp,
                            contentDescription = post.authorName
                        )
                    } else {
                        Icon(Icons.Default.Pets, null, tint = BrandOrangeSoft, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(modifier = Modifier.width(LeoDimens.SpaceCompact))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = post.authorName,
                        style = LeoCardTitle,
                        color = BrandText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (onAuthorClick != null) {
                            Modifier.clickable { onAuthorClick(post.authorId) }
                        } else {
                            Modifier
                        }
                    )
                    val meta = buildList {
                        if (!petName.isNullOrBlank()) add(petName)
                        if (!post.locationText.isNullOrBlank()) add(post.locationText)
                        add(post.displayDate())
                    }.joinToString(" · ")
                    Text(text = meta, style = LeoCaption, color = MutedText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (special != null) {
                    SocialTypeBadge(label = special.label, container = special.container, content = special.content)
                    Spacer(modifier = Modifier.width(LeoDimens.SpaceSm))
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Más opciones", tint = BrandText)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        onSaveClick?.let { save ->
                            DropdownMenuItem(
                                text = { Text(if (isSaved) "Quitar de guardados" else "Guardar") },
                                onClick = { menuExpanded = false; save() }
                            )
                        }
                        onReportClick?.let { report ->
                            DropdownMenuItem(
                                text = { Text("Reportar") },
                                onClick = { menuExpanded = false; report() }
                            )
                        }
                        onBlockClick?.let { block ->
                            DropdownMenuItem(
                                text = { Text("Bloquear autor") },
                                onClick = { menuExpanded = false; block() }
                            )
                        }
                    }
                }
            }

            if (post.imageUrl != null) {
                PetImage(
                    imageUrl = post.imageUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    cornerRadius = 0.dp,
                    contentDescription = post.title
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(BrandCream),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Pets, contentDescription = null, tint = BrandOrangeSoft, modifier = Modifier.size(48.dp))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LeoDimens.SpaceSm, vertical = LeoDimens.SpaceSm),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row {
                    IconButton(onClick = { onLikeClick?.invoke() }, enabled = onLikeClick != null) {
                        Icon(
                            if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Me gusta",
                            tint = if (isLiked) BrandOrange else BrandText
                        )
                    }
                    IconButton(onClick = { onCommentClick?.invoke() }, enabled = onCommentClick != null) {
                        Icon(Icons.AutoMirrored.Filled.Comment, contentDescription = "Comentar", tint = BrandText)
                    }
                    IconButton(onClick = { onShareClick?.invoke() }, enabled = onShareClick != null) {
                        Icon(Icons.Default.Share, contentDescription = "Compartir", tint = BrandText)
                    }
                }
                IconButton(onClick = { onSaveClick?.invoke() }, enabled = onSaveClick != null) {
                    Icon(
                        if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Guardar",
                        tint = if (isSaved) BrandOrangeSoft else BrandText
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceSm)) {
                if (post.likeCount > 0) {
                    Text(
                        text = "${post.likeCount} me gusta",
                        style = LeoCaption.copy(fontWeight = FontWeight.SemiBold),
                        color = BrandText
                    )
                }
                val body = listOfNotNull(
                    post.title.takeIf { it.isNotBlank() },
                    post.content.takeIf { it.isNotBlank() }
                ).joinToString("\n")
                if (body.isNotBlank()) {
                    Text(
                        text = body,
                        style = LeoCaption,
                        color = BrandText,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (post.commentCount > 0) {
                    Text(
                        text = "Ver los ${post.commentCount} comentarios",
                        style = LeoCaption,
                        color = MutedText,
                        modifier = Modifier
                            .padding(top = LeoDimens.SpaceSm)
                            .clickable(enabled = onCommentClick != null) { onCommentClick?.invoke() }
                    )
                }
                if (special != null && onSpecialCta != null) {
                    LeoPrimaryButton(
                        text = special.cta,
                        onClick = onSpecialCta,
                        modifier = Modifier.padding(top = LeoDimens.SpaceCompact)
                    )
                }
                Text(
                    text = post.displayDate(),
                    style = LeoCaption,
                    color = MutedText,
                    modifier = Modifier.padding(top = LeoDimens.SpaceSm, bottom = LeoDimens.SpaceSm)
                )
            }
        }
    }
}

private data class SpecialBadge(val label: String, val cta: String, val container: Color, val content: Color)

private fun specialBadgeFor(type: PostType): SpecialBadge? = when (type) {
    PostType.ADOPTION -> SpecialBadge("ADOPCIÓN", "Ver adopción", BrandGreenContainer, BrandGreen)
    PostType.LOST_FOUND -> SpecialBadge("PERDIDO / ENCONTRADO", "Ver aviso", UrgentContainer, UrgentRed)
    PostType.URGENT -> SpecialBadge("URGENTE", "Ver detalle", UrgentContainer, UrgentRed)
    PostType.PROMO -> SpecialBadge("PROMO", "Ver más", BrandOrangeContainer, BrandOrange)
    else -> null
}

@Composable
private fun SocialTypeBadge(label: String, container: Color, content: Color) {
    Text(
        text = label,
        style = LeoCaption.copy(fontWeight = FontWeight.Bold),
        color = content,
        modifier = Modifier
            .background(container, RoundedCornerShape(LeoDimens.RadiusChip))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFF6EA, widthDp = 390, name = "SocialPostImagePreview")
@Composable
private fun SocialPostImagePreview() {
    ComunidappTheme {
        LeoSocialPostCard(
            post = FeedPost(
                id = "p1",
                authorId = "u1",
                authorName = "Ana Pets",
                type = PostType.GENERAL,
                title = "Paseo de domingo",
                content = "Luna disfrutando el parque #mascotas #paseo",
                imageUrl = null,
                likeCount = 24,
                commentCount = 3,
                date = "Hoy"
            ),
            isLiked = true,
            petName = "Luna"
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFF6EA, widthDp = 390, name = "SocialPostSpecialPreview")
@Composable
private fun SocialPostSpecialPreview() {
    ComunidappTheme {
        LeoSocialPostCard(
            post = FeedPost(
                id = "p2",
                authorId = "u2",
                authorName = "Refugio Sol",
                type = PostType.ADOPTION,
                title = "Toby busca hogar",
                content = "Cachorro mansito, listo para adoptar.",
                locationText = "CABA",
                likeCount = 56,
                commentCount = 12,
                date = "Ayer"
            ),
            onSpecialCta = {}
        )
    }
}
