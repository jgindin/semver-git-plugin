package io.wusa

import io.wusa.exception.*
import io.wusa.extension.SemverGitPluginExtension
import io.wusa.incrementer.VersionIncrementer
import org.gradle.api.GradleException
import org.gradle.api.Project

class VersionService(private var project: Project) {
    private val semverGitPluginExtension: SemverGitPluginExtension = project.extensions.getByType(SemverGitPluginExtension::class.java)

    @Throws(GradleException::class)
    fun getVersion(): Version {
        val versionFactory: IVersionFactory = SemanticVersionFactory()

        return try {
            getCurrentVersion(versionFactory)
        } catch (ex: IllegalArgumentException) {
            throw GradleException("The current tag is not a semantic version.")
        } catch (ex: NoCurrentTagFoundException) {
            handleNoCurrentTagFound(versionFactory)
        }
    }

    @Throws(GradleException::class)
    private fun handleNoCurrentTagFound(versionFactory: IVersionFactory): Version {
        return try {
            val lastVersion = getLastVersion(versionFactory)
            incrementVersion(lastVersion)
        } catch (ex: NoValidSemverTagFoundException) {
            throw GradleException(ex.localizedMessage)
        } catch (ex: NoIncrementerFoundException) {
            throw GradleException(ex.localizedMessage)
        } catch (ex: NoLastTagFoundException) {
            buildInitialVersion(versionFactory)
        }
    }

    private fun buildInitialVersion(versionFactory: IVersionFactory): Version {
        return try {
            buildInitialVersionForTag(versionFactory)
        } catch (ex: NoCurrentCommitFoundException) {
            buildInitialVersionWithNoTag()
        }
    }

    private fun buildInitialVersionWithNoTag() = Version(0, 1, 0, "", "", null)

    private fun buildInitialVersionForTag(versionFactory: IVersionFactory): Version {
        val sha = GitService.currentCommit(project, true)
        val isDirty = GitService.isDirty(project)
        val count = GitService.count(project)
        val version = versionFactory.createFromString(semverGitPluginExtension.initialVersion)
        version.suffix = Suffix(count, sha, isDirty)
        return version
    }

    private fun getLastVersion(versionFactory: IVersionFactory) =
            versionFactory.createFromString(GitService.lastTag(project))

    private fun getCurrentVersion(versionFactory: IVersionFactory) =
            versionFactory.createFromString(GitService.currentTag(project))

    private fun incrementVersion(version: Version): Version {
        val regexIncrementerPair = RegexResolver.findMatchingRegex(semverGitPluginExtension.branches, semverGitPluginExtension.info.branch.name)
        regexIncrementerPair?.let {
            return VersionIncrementer.getVersionIncrementerByName(regexIncrementerPair.incrementer).increment(version)
        } ?: run {
            return VersionIncrementer.getVersionIncrementerByName(SemverGitPluginExtension.DEFAULT_INCREMENTER).increment(version)
        }
    }
}
