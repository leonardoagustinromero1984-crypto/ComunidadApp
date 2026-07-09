package com.comunidapp.app.data.remote.storage

object StoragePaths {
    fun userAvatar(userId: String) = "users/$userId/avatar.jpg"
    fun petPhoto(userId: String, petId: String) = "users/$userId/pets/$petId/photo.jpg"
    fun postImage(postId: String) = "posts/$postId/image.jpg"
    fun adoptionImage(adoptionId: String) = "adoptions/$adoptionId/image.jpg"
    fun lostFoundImage(postId: String) = "lost_found/$postId/image.jpg"
}
