package com.facemorphapp.domain.usecase

import com.facemorphapp.domain.model.TargetFace
import com.facemorphapp.domain.repository.TargetFaceRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTargetFacesUseCase @Inject constructor(
    private val repository: TargetFaceRepository
) {
    operator fun invoke(): Flow<List<TargetFace>> = repository.getBundledTargets()
}
