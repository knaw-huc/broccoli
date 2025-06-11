package nl.knaw.huc.broccoli.core

import nl.knaw.huc.broccoli.config.BrintaConfiguration
import nl.knaw.huc.broccoli.config.NamedViewConfiguration
import nl.knaw.huc.broccoli.service.anno.AnnoRepo
import nl.knaw.huc.broccoli.service.text.TextRepo

data class Project(
    val name: String,
    val textType: String,
    val topTierBodyType: String,
    val views: Map<String, NamedViewConfiguration>,
    val brinta: BrintaConfiguration,
    val textRepo: TextRepo,
    val annoRepo: AnnoRepo,
)
