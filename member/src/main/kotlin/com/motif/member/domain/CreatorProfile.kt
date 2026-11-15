package com.motif.member.domain

import com.motif.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

@Entity
class CreatorProfile(
    memberId: Long,
    bio: String?,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, unique = true, updatable = false)
    val memberId: Long = memberId

    @Column(length = 500)
    var bio: String? = bio
        protected set

    @Column(length = 500)
    var profileImageUrl: String? = null
        protected set

    // follow 기준 비정규화
    @Column(nullable = false)
    var followerCount: Int = 0
        protected set
}
