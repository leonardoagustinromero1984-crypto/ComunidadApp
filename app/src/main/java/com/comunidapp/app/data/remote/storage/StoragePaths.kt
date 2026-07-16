package com.comunidapp.app.data.remote.storage

object StoragePaths {
    /** Path oficial M02: ownership validado en Storage RLS. */
    fun userAvatar(userId: String, filename: String = "avatar.jpg"): String =
        "users/$userId/avatar/$filename"

    /** Path oficial M03: ownership por organizationId + permiso organization.update. */
    fun organizationLogo(organizationId: String, filename: String = "logo.jpg"): String =
        "organizations/$organizationId/logo/$filename"

    fun organizationCover(organizationId: String, filename: String = "cover.jpg"): String =
        "organizations/$organizationId/cover/$filename"

    fun petPhoto(userId: String, petId: String) = "users/$userId/pets/$petId/photo.jpg"
    fun postImage(postId: String) = "posts/$postId/image.jpg"
    fun adoptionImage(adoptionId: String) = "adoptions/$adoptionId/image.jpg"
    fun lostFoundImage(postId: String) = "lost_found/$postId/image.jpg"
}
