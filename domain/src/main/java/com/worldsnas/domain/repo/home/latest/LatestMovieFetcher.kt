package com.worldsnas.domain.repo.home.latest

import com.worldsnas.domain.model.servermodels.MovieServerModel
import com.worldsnas.domain.model.servermodels.ResultsServerModel
import com.worldsnas.domain.repo.home.HomeAPI
import com.worldsnas.panther.Fetcher
import retrofit2.Response
import javax.inject.Inject

class LatestMovieFetcher @Inject constructor(
    private val api: HomeAPI
) : Fetcher<LatestMovieRequestParam, ResultsServerModel<MovieServerModel>> {
    override suspend fun fetch(param: LatestMovieRequestParam): Response<ResultsServerModel<MovieServerModel>> =
        api.getLatestMovie(param.page)
}