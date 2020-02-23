package com.worldsnas.domain.repo.home.latest

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.worldsnas.core.ErrorHolder
import com.worldsnas.core.listMerge
import com.worldsnas.core.toListFlow
import com.worldsnas.db.LatestMoviePersister
import com.worldsnas.db.Movie
import com.worldsnas.domain.helpers.getErrorRepoModel
import com.worldsnas.domain.helpers.isEmptyBody
import com.worldsnas.domain.helpers.isNotSuccessful
import com.worldsnas.domain.model.PageModel
import com.worldsnas.domain.model.repomodel.MovieRepoModel
import com.worldsnas.domain.model.servermodels.MovieServerModel
import com.worldsnas.domain.model.servermodels.ResultsServerModel
import com.worldsnas.panther.Fetcher
import com.worldsnas.panther.Mapper
import io.reactivex.Observable
import io.reactivex.Single
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.rx2.asObservable
import retrofit2.Response
import timber.log.Timber
import java.util.*
import javax.inject.Inject

interface LatestMovieRepo {
    fun receiveAndUpdate(param: PageModel): Flow<Either<ErrorHolder, List<MovieRepoModel>>>
    fun fetch(param: LatestMovieRepoParamModel): Single<LatestMovieRepoOutputModel>
    fun memCache(): Single<LatestMovieRepoOutputModel.Success>
}

private const val MOVIE_PAGE_SIZE = 20

class LatestMovieRepoImpl @Inject constructor(
    private val fetcher: Fetcher<LatestMovieRequestParam, ResultsServerModel<MovieServerModel>>,
    private val movieServerRepoMapper: Mapper<MovieServerModel, MovieRepoModel>,
    private val moviePersister: LatestMoviePersister,
    private val movieRepoDBMapper: Mapper<MovieRepoModel, Movie>,
    private val movieDBRepoMapper: Mapper<Movie, MovieRepoModel>
) : LatestMovieRepo {

    var list: MutableList<MovieRepoModel> = mutableListOf()

    override fun memCache(): Single<LatestMovieRepoOutputModel.Success> =
        Single.just(
            LatestMovieRepoOutputModel.Success(
                emptyList(),
                list
            )
        )

    override fun receiveAndUpdate(param: PageModel): Flow<Either<ErrorHolder, List<MovieRepoModel>>> =
        when (param) {
            PageModel.First -> loadFirstPage()
            is PageModel.NextPage -> loadNextPage()
        }

    private fun loadFirstPage(): Flow<Either<ErrorHolder, List<MovieRepoModel>>> = flow {
        val entireDb: List<MovieRepoModel> =
            moviePersister.observeMovies()
                .take(1)
                .first()
                .map {
                    movieDBRepoMapper.map(it)
                }

        emit(entireDb.right())

        val serverFirstPageResponse = fetcher.fetch(LatestMovieRequestParam(Date(), 1))

        if (serverFirstPageResponse.isNotSuccessful || serverFirstPageResponse.body() == null) {
            list = entireDb.toMutableList()
            emit(entireDb.right())
            emit(serverFirstPageResponse.getErrorRepoModel().left())
            return@flow
        }

        val serverFirstPage: List<MovieRepoModel> = serverFirstPageResponse.body()!!.list.map {
            movieServerRepoMapper.map(it)
        }

        val dbValid = serverFirstPage.map { it.id }
            .asFlow()
            .toListFlow()
            .flatMapConcat {
                moviePersister.findAny(it)
            }
            .map {
                it != null
            }
            .first()

        if (!dbValid) {
            moviePersister.clearMovies()
            list.clear()
        }

        moviePersister.insertMovies(serverFirstPage.map {
            movieRepoDBMapper.map(it)
        })

        list.addAll(
            moviePersister
                .observeMovies()
                .first()
                .map {
                    movieDBRepoMapper.map(it)
                }
        )

        emit(list.right())
    }

    private fun loadNextPage(): Flow<Either<ErrorHolder, List<MovieRepoModel>>> =
        moviePersister.movieCount()
            .map {
                (it / MOVIE_PAGE_SIZE) + 1
            }
            .filter { it > 1 }
            .map { page ->
                fetcher.fetch(LatestMovieRequestParam(Date(), page.toInt()))
            }
            .listMerge { responseFlow ->
                listOf(
                    responseFailed(responseFlow),
                    responseSuccess(responseFlow)
                )
            }
            .flowOn(Dispatchers.Default)

    private fun responseFailed(responseFlow: Flow<Response<ResultsServerModel<MovieServerModel>>>) =
        responseFlow
            .filter { response ->
                response.isNotSuccessful || response.isEmptyBody || response.body()!!.list.isEmpty()
            }
            .map { response ->
                response.getErrorRepoModel().left()
            }


    private fun responseSuccess(responseFlow: Flow<Response<ResultsServerModel<MovieServerModel>>>) =
        responseFlow
            .filter { response ->
                response.body()?.list?.isNotEmpty() ?: false
            }
            .map { response ->
                response.body()?.list!!
            }
            .map { serverList ->
                serverList.map {
                    movieServerRepoMapper.map(it)
                }
            }
            .map { repoList ->
                moviePersister.insertMovies(repoList.map { movieRepoDBMapper.map(it) })
            }
            .flatMapConcat {
                moviePersister.observeMovies()
                    .take(1)
            }
            .map {
                it.map { movie ->
                    movieDBRepoMapper.map(movie)
                }.right()
            }

    override fun fetch(param: LatestMovieRepoParamModel): Single<LatestMovieRepoOutputModel> =
        flow {
            emit(fetcher.fetch(LatestMovieRequestParam(Date(), param.page)))
        }
            .asObservable()
            .publish { publish ->
                Observable.merge(
                    publish
                        .filter { it.isNotSuccessful || it.body() == null }
                        .map { LatestMovieRepoOutputModel.Error(it.getErrorRepoModel()) },
                    publish
                        .filter { it.isSuccessful && it.body() != null }
                        .map {
                            it
                                .body()!!
                                .list
                                .map { movie ->
                                    movieServerRepoMapper
                                        .map(movie)
                                }
                        }
                        .doOnNext {
                            if (param.page == 1) {
                                list = it.toMutableList()
                            } else {
                                list.addAll(it)
                            }
                        }
                        .map {
                            LatestMovieRepoOutputModel.Success(it, list)
                        }
                )
            }
            .firstOrError()
}