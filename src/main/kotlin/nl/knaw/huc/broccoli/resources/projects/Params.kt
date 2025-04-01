package nl.knaw.huc.broccoli.resources.projects

class Params(
    val from: Int,
    val size: Int,
    val fragmentSize: Int,
    val sortBy: String,
    val sortOrder: ProjectsResource.SortOrder
)
