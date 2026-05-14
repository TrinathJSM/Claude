package com.facemorphapp.domain.usecase

import com.facemorphapp.domain.model.MorphResult
import com.facemorphapp.domain.repository.FaceMorphRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetRecentMorphsUseCase @Inject constructor(
    private val repository: FaceMorphRepository
) {
    operator fun invoke(limit: Int = 20): Flow<List<MorphResult>> =
        repository.getRecentResults(limit)
}
