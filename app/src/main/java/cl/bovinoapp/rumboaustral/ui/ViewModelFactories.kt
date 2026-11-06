package cl.bovinoapp.rumboaustral.ui

import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.bovinoapp.rumboaustral.AppContainer
import cl.bovinoapp.rumboaustral.ui.downloads.DownloadsViewModel
import cl.bovinoapp.rumboaustral.ui.labels.LabelsViewModel
import cl.bovinoapp.rumboaustral.ui.map.MapViewModel
import cl.bovinoapp.rumboaustral.ui.navigation.NavigationViewModel
import cl.bovinoapp.rumboaustral.ui.points.PointsViewModel

fun mapViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer { MapViewModel(container.pointsRepository, container.locationTracker) }
}

fun pointsViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer { PointsViewModel(container.pointsRepository) }
}

fun labelsViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer { LabelsViewModel(container.labelsRepository) }
}

fun navigationViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer {
        NavigationViewModel(
            container.compassTracker
        )
    }
}

fun downloadsViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer { DownloadsViewModel(container.offlineRegionsRepository) }
}
