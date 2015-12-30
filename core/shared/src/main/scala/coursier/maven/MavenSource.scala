package coursier.maven

import coursier.core._

case class MavenSource(
  root: String,
  ivyLike: Boolean,
  changing: Option[Boolean] = None,
  /** See doc on MavenRepository */
  sbtAttrStub: Boolean
) extends Artifact.Source {

  import Repository._
  import MavenRepository._

  private implicit class DocSourcesArtifactExtensions(val underlying: Artifact) {
    def withJavadocSources: Artifact = {
      val base = underlying.url.stripSuffix(".jar")
      underlying.copy(extra = underlying.extra ++ Seq(
        "sources" -> Artifact(
          base + "-sources.jar",
          Map.empty,
          Map.empty,
          Attributes("jar", "src"),  // Are these the right attributes?
          changing = underlying.changing
        )
          .withDefaultChecksums
          .withDefaultSignature,
        "javadoc" -> Artifact(
          base + "-javadoc.jar",
          Map.empty,
          Map.empty,
          Attributes("jar", "javadoc"), // Same comment as above
          changing = underlying.changing
        )
          .withDefaultChecksums
          .withDefaultSignature
      ))
    }
  }

  def artifacts(
    dependency: Dependency,
    project: Project,
    overrideClassifiers: Option[Seq[String]]
  ): Seq[Artifact] = {

    def artifactOf(module: Module, publication: Publication) = {
      def ivyLikePath0(subDir: String, baseSuffix: String, ext: String) =
        ivyLikePath(
          module.organization,
          MavenRepository.dirModuleName(module, sbtAttrStub),
          module.name,
          project.version,
          subDir,
          baseSuffix,
          ext
        )

      val path =
        if (ivyLike)
          ivyLikePath0(publication.`type` + "s", "", publication.ext)
        else {
          val versioning =
            project
              .snapshotVersioning
              .flatMap(versioning =>
                mavenVersioning(versioning, publication.classifier, publication.`type`)
              )

          module.organization.split('.').toSeq ++ Seq(
            MavenRepository.dirModuleName(module, sbtAttrStub),
            project.version,
            s"${module.name}-${versioning getOrElse project.version}${Some(publication.classifier).filter(_.nonEmpty).map("-" + _).mkString}.${publication.ext}"
          )
        }

      val changing0 = changing.getOrElse(project.version.contains("-SNAPSHOT"))
      var artifact =
        Artifact(
          root + path.mkString("/"),
          Map.empty,
          Map.empty,
          publication.attributes,
          changing = changing0
        )
          .withDefaultChecksums

      if (publication.ext == "jar") {
        artifact = artifact.withDefaultSignature
      }

      artifact
    }

    overrideClassifiers match {
      case Some(classifiers) =>
        val classifiersSet = classifiers.toSet
        project.publications.collect {
          case (_, p) if classifiersSet(p.classifier) =>
            artifactOf(dependency.module, p)
        }
      case None =>
        Seq(
          artifactOf(
            dependency.module,
            dependency.attributes.publication(
              dependency.module.name,
              dependency.attributes.`type`
            )
          )
        )
    }
  }
}
