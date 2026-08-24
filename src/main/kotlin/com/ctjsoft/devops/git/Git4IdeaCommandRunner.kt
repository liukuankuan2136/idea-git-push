package com.ctjsoft.devops.git

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler

class Git4IdeaCommandRunner(
    private val project: Project,
    private val root: VirtualFile,
) : GitCommandRunner {
    override fun run(arguments: List<String>): GitCommandResult {
        require(arguments.isNotEmpty())
        val command = when (arguments.first()) {
            "commit" -> GitCommand.COMMIT
            "diff" -> GitCommand.DIFF
            "push" -> GitCommand.PUSH
            "remote" -> GitCommand.REMOTE
            "reset" -> GitCommand.RESET
            "rev-list" -> GitCommand.REV_LIST
            "rev-parse" -> GitCommand.REV_PARSE
            else -> error("Unsupported Git command: ${arguments.first()}")
        }
        val handler = GitLineHandler(project, root, command).apply {
            addParameters(arguments.drop(1))
            setSilent(true)
        }
        val result = Git.getInstance().runCommand(handler)
        return GitCommandResult(
            exitCode = result.exitCode,
            stdout = result.outputAsJoinedString,
            stderr = result.errorOutputAsJoinedString,
        )
    }
}

