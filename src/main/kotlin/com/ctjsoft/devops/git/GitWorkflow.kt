package com.ctjsoft.devops.git

import com.ctjsoft.devops.core.DevOpsException
import com.ctjsoft.devops.core.ErrorKind

data class GitCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val successful: Boolean get() = exitCode == 0
}

fun interface GitCommandRunner {
    fun run(arguments: List<String>): GitCommandResult
}

data class BranchPushState(
    val hasUpstream: Boolean,
    val upstream: String? = null,
    val hasUnpushedCommits: Boolean,
)

data class PushTarget(
    val hasUpstream: Boolean,
    val remoteName: String? = null,
    val branchName: String? = null,
)

class GitWorkflow(private val git: GitCommandRunner) {
    fun hasStagedChanges(): Boolean = git.run(listOf("diff", "--cached", "--quiet")).exitCode != 0

    fun branchState(): BranchPushState {
        val upstreamResult = git.run(listOf("rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}"))
        val upstream = upstreamResult.stdout.trim().takeIf { upstreamResult.successful && it.isNotBlank() }
        val unpushed = if (upstream != null) {
            git.run(listOf("rev-list", "--count", "$upstream..HEAD")).stdout.trim().toIntOrNull()?.let { it > 0 } == true
        } else {
            git.run(listOf("rev-parse", "HEAD")).successful
        }
        return BranchPushState(upstream != null, upstream, unpushed)
    }

    fun currentBranch(): String? = git.run(listOf("rev-parse", "--abbrev-ref", "HEAD"))
        .takeIf(GitCommandResult::successful)?.stdout?.trim()?.takeIf { it.isNotBlank() && it != "HEAD" }

    fun remotes(): List<String> = git.run(listOf("remote"))
        .takeIf(GitCommandResult::successful)?.stdout?.lineSequence()?.map(String::trim)?.filter(String::isNotBlank)?.toList().orEmpty()

    fun originUrl(): String? = git.run(listOf("remote", "get-url", "origin"))
        .takeIf(GitCommandResult::successful)?.stdout?.trim()?.takeIf(String::isNotBlank)

    fun commit(message: String) {
        validateCommitMessage(message)
        requireSuccess(git.run(listOf("commit", "-m", message)), "提交代码失败")
    }

    fun amend(message: String) {
        if (!branchState().hasUnpushedCommits) {
            throw DevOpsException("当前没有未推送的 commit 可修改。", ErrorKind.GIT_PRECONDITION)
        }
        validateCommitMessage(message)
        requireSuccess(git.run(listOf("commit", "--amend", "--only", "-m", message)), "修改 commit 失败")
    }

    fun push(target: PushTarget) {
        val result = if (target.hasUpstream) {
            git.run(listOf("push"))
        } else {
            val remote = target.remoteName?.takeIf(String::isNotBlank)
                ?: throw DevOpsException("未选择远程仓库。", ErrorKind.GIT_PRECONDITION)
            val branch = target.branchName?.takeIf(String::isNotBlank)
                ?: throw DevOpsException("未填写远程分支。", ErrorKind.GIT_PRECONDITION)
            git.run(listOf("push", "--set-upstream", remote, branch))
        }
        requireSuccess(result, "推送代码失败")
    }

    fun recoverCommit() {
        git.run(listOf("reset", "--soft", "HEAD~1"))
    }

    fun recoverAmend() {
        if (git.run(listOf("rev-parse", "HEAD@{1}")).successful) {
            git.run(listOf("reset", "--soft", "HEAD@{1}"))
        }
    }

    companion object {
        fun validateCommitMessage(message: String) {
            if (message.length !in 10..500) {
                throw DevOpsException("commit message 长度必须在 10 到 500 个字符之间。", ErrorKind.VALIDATION)
            }
            if (!Regex("\\sscrum -e\\s+\\S+").containsMatchIn(message)) {
                throw DevOpsException("commit message 必须包含小写指令 scrum -e。", ErrorKind.VALIDATION)
            }
            val normal = Regex("^(feat|fix|perf|refactor|test|style|build|chore|upd|doc):", RegexOption.IGNORE_CASE)
            if (!normal.containsMatchIn(message) && !message.startsWith("Merge ")) {
                throw DevOpsException("commit message 必须以合法 type 开头。", ErrorKind.VALIDATION)
            }
        }
    }

    private fun requireSuccess(result: GitCommandResult, prefix: String) {
        if (!result.successful) {
            val detail = sanitizeGitOutput(result.stderr.ifBlank { result.stdout }).ifBlank { "exit ${result.exitCode}" }
            throw DevOpsException("$prefix：$detail", ErrorKind.GIT_COMMAND)
        }
    }

    private fun sanitizeGitOutput(output: String): String = output
        .replace(Regex("https?://[^/@\\s]+@"), "https://***@")
        .trim()
}

