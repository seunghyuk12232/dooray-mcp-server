package com.bifos.dooray.mcp.types

import kotlinx.serialization.Serializable

/** 프로젝트 정보 */
@Serializable data class ProjectInfo(val id: String, val code: String)

/** 업무의 상위 업무 정보 */
@Serializable data class ParentPost(val id: String, val number: Int, val subject: String)

/** 마일스톤 정보 */
@Serializable data class Milestone(val id: String, val name: String)

/** 태그 정보 */
@Serializable data class Tag(val id: String)

/** 워크플로우 정보 */
@Serializable data class Workflow(val id: String, val name: String)

/** 이메일 유저 정보 */
@Serializable data class EmailUser(val emailAddress: String, val name: String)

/** 그룹 정보 */
@Serializable data class Group(val projectMemberGroupId: String, val members: List<Member>)

/** 업무 관련 사용자 - 기본 타입 */
@Serializable
data class PostUser(
        val type: String, // "member", "emailUser", "group"
        val member: Member? = null,
        val emailUser: EmailUser? = null,
        val group: Group? = null,
        val workflow: Workflow? = null
)

/** 업무 관련 사용자들 */
@Serializable
data class PostUsers(val from: PostUser, val to: List<PostUser>, val cc: List<PostUser>)

/** 업무 본문 정보 */
@Serializable
data class PostBody(
        val mimeType: String, // "text/html", "text/x-markdown"
        val content: String
)

/** 첨부 파일 정보 */
@Serializable data class PostFile(val id: String, val name: String, val size: Long)

/** 업무 기본 정보 (목록용) */
@Serializable
data class Post(
        val id: String,
        val subject: String,
        val project: ProjectInfo,
        val taskNumber: String,
        val closed: Boolean,
        val createdAt: String,
        val dueDate: String? = null,
        val dueDateFlag: Boolean? = null,
        val updatedAt: String,
        val number: Int,
        val priority: String,
        val parent: ParentPost? = null,
        val workflowClass: String, // "registered", "working", "closed"
        val workflow: Workflow,
        val milestone: Milestone? = null,
        val tags: List<Tag> = emptyList(),
        val users: PostUsers,
        val fileIdList: List<String> = emptyList()
)

/** 업무 상세 정보 */
@Serializable
data class PostDetail(
        val id: String,
        val subject: String,
        val project: ProjectInfo,
        val taskNumber: String,
        val closed: Boolean,
        val createdAt: String,
        val dueDate: String? = null,
        val dueDateFlag: Boolean? = null,
        val updatedAt: String,
        val number: Int,
        val priority: String,
        val parent: ParentPost? = null,
        val workflowClass: String, // "registered", "working", "closed"
        val workflow: Workflow,
        val milestone: Milestone? = null,
        val tags: List<Tag> = emptyList(),
        val body: PostBody,
        val users: PostUsers,
        val files: List<PostFile> = emptyList(),
        val fileIdList: List<String> = emptyList()
)

/** 업무 생성 요청 */
@Serializable
data class CreatePostRequest(
        val parentPostId: String? = null,
        val users: CreatePostUsers,
        val subject: String,
        val body: PostBody,
        val dueDate: String? = null,
        val dueDateFlag: Boolean = true,
        val milestoneId: String? = null,
        val tagIds: List<String> = emptyList(),
        val priority: String = "none" // "highest", "high", "normal", "low", "lowest", "none"
)

/** 업무 생성용 사용자 정보 */
@Serializable
data class CreatePostUsers(
        val to: List<CreatePostUser>,
        val cc: List<CreatePostUser> = emptyList()
)

/** 업무 생성용 사용자 */
@Serializable
data class CreatePostUser(
        val type: String, // "member", "emailUser"
        val member: Member? = null,
        val emailUser: EmailUser? = null
)

/** 업무 수정 요청 */
@Serializable
data class UpdatePostRequest(
        val users: CreatePostUsers,
        val subject: String,
        val body: PostBody,
        val version: Int? = null,
        val dueDate: String? = null,
        val dueDateFlag: Boolean = true,
        val milestoneId: String? = null,
        val tagIds: List<String> = emptyList(),
        val priority: String = "none"
)

/** 워크플로우 변경 요청 */
@Serializable data class SetWorkflowRequest(val workflowId: String)

/** 상위 업무 설정 요청 */
@Serializable data class SetParentPostRequest(val parentPostId: String)

/** 업무 생성 응답 */
@Serializable data class CreatePostResponse(val id: String)

/** 업무 목록 API 응답 */
@Serializable
data class PostListApiResponse(
        val header: DoorayApiHeader,
        val result: List<Post>,
        val totalCount: Int
)

// API 응답 타입 별칭들
typealias PostListResponse = PostListApiResponse

typealias PostDetailResponse = DoorayApiResponse<PostDetail>

/**
 * GET /project/v1/posts/{post-id} (프로젝트 비지정 단건 조회) 응답용.
 * 이 글로벌 엔드포인트는 project-scoped 조회와 달리 workflow/workflowClass 등을
 * 생략하므로, project-scoped PostDetail 과 별도로 위험 필드를 nullable 로 둔다.
 */
@Serializable
data class PostByIdDetail(
        val id: String,
        val subject: String,
        val project: ProjectInfo,
        val taskNumber: String,
        val closed: Boolean,
        val createdAt: String,
        val dueDate: String? = null,
        val dueDateFlag: Boolean? = null,
        val updatedAt: String,
        val number: Int,
        val priority: String,
        val parent: ParentPost? = null,
        val workflowClass: String? = null,
        val workflow: Workflow? = null,
        val milestone: Milestone? = null,
        val tags: List<Tag> = emptyList(),
        val body: PostBody? = null,
        val users: PostUsers? = null,
        val files: List<PostFile> = emptyList(),
        val fileIdList: List<String> = emptyList()
)

typealias PostByIdResponse = DoorayApiResponse<PostByIdDetail>

typealias CreatePostApiResponse = DoorayApiResponse<CreatePostResponse>

typealias UpdatePostResponse = DoorayApiUnitResponse

// ============ 업무 첨부파일 / 이동 관련 타입 ============

/** 첨부파일 업로드 응답 result */
@Serializable data class PostFileUploadResult(val id: String)

/** 첨부파일 메타 정보 (목록/상세 조회용) */
@Serializable
data class PostFileMeta(
        val id: String,
        val name: String? = null,
        val size: Long? = null,
        val mimeType: String? = null,
        val createdAt: String? = null,
        val creator: PostUser? = null
)

/** 첨부파일 목록 API 응답 */
@Serializable
data class PostFileListApiResponse(
        val header: DoorayApiHeader,
        val result: List<PostFileMeta> = emptyList(),
        val totalCount: Int? = null
)

/** 업무 이동 요청 */
@Serializable
data class MovePostRequest(
        val targetProjectId: String? = null, // 미지정 시 동일 프로젝트로 간주
        val includeSubPosts: Boolean = true
)

/** 업무 이동 응답의 프로젝트 정보 (move 응답의 project 는 id 만 포함) */
@Serializable data class MovePostProject(val id: String? = null)

/** 업무 이동 응답 result (모든 필드 nullable — 서버가 result 를 축약/생략해도 성공을 실패로 뒤집지 않도록) */
@Serializable
data class MovePostResult(val post: PostInfo? = null, val project: MovePostProject? = null)

// API 응답 타입 별칭들
typealias UploadPostFileResponse = DoorayApiResponse<PostFileUploadResult>

typealias PostFileListResponse = PostFileListApiResponse

/** 업무 이동 API 응답 (result 키가 생략된 200 응답도 관용하도록 default 지정) */
@Serializable
data class MovePostApiResponse(val header: DoorayApiHeader, val result: MovePostResult? = null)

typealias MovePostResponse = MovePostApiResponse

typealias DeletePostFileResponse = DoorayApiUnitResponse

/** 업무 댓글 본문 */
@Serializable data class PostCommentBody(val mimeType: String, val content: String)

/** 업무 댓글 첨부파일 */
@Serializable data class PostCommentFile(val id: String, val name: String, val size: Long)

/** 업무 댓글 정보 */
@Serializable
data class PostComment(
        val id: String,
        val post: PostInfo,
        val type: String, // comment, event
        val subtype: String, // general, from_email, sent_email
        val createdAt: String,
        val modifiedAt: String? = null,
        val creator: PostUser,
        val mailUsers: MailUsers? = null,
        val body: PostCommentBody,
        val files: List<PostCommentFile>? = null
)

/** 댓글 생성 요청 */
@Serializable data class CreateCommentRequest(val body: PostCommentBody)

/** 댓글 생성 응답 */
@Serializable data class CreateCommentResponse(val id: String)

/** 댓글 수정 요청 */
@Serializable data class UpdateCommentRequest(val body: PostCommentBody)

/** 댓글 목록 응답 구조 */
@Serializable
data class PostCommentListApiResponse(
        val header: DoorayApiHeader,
        val result: List<PostComment>,
        val totalCount: Int
)

// API 응답 타입 별칭들
typealias PostCommentListResponse = PostCommentListApiResponse

typealias PostCommentDetailResponse = DoorayApiResponse<PostComment>

typealias CreateCommentApiResponse = DoorayApiResponse<CreateCommentResponse>

typealias UpdateCommentResponse = DoorayApiUnitResponse

typealias DeleteCommentResponse = DoorayApiUnitResponse

/** 업무 기본 정보 (댓글에서 참조용) */
@Serializable data class PostInfo(val id: String)

/** 메일 사용자 정보 */
@Serializable
data class MailUsers(
        val from: EmailUser? = null,
        val to: List<EmailUser> = emptyList(),
        val cc: List<EmailUser> = emptyList()
)
