package com.worldsnas.moviedetail

import com.worldsnas.base.toErrorState
import com.worldsnas.core.delayEvent
import com.worldsnas.domain.repo.moviedetail.MovieDetailRepo
import com.worldsnas.domain.repo.moviedetail.model.MovieDetailRepoOutPutModel
import com.worldsnas.domain.repo.moviedetail.model.MovieDetailRepoParamModel
import com.worldsnas.mvi.MviProcessor
import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.ofType
import javax.inject.Inject

class MovieDetailProcessor @Inject constructor(
    repo: MovieDetailRepo
) : MviProcessor<MovieDetailIntent, MovieDetailResult> {
    override val actionProcessor =
        ObservableTransformer<MovieDetailIntent, MovieDetailResult> { intents ->
            intents
                .ofType<MovieDetailIntent.Initial>()
                .compose(initialProcessor)
                .observeOn(AndroidSchedulers.mainThread())
        }

    private val initialProcessor =
        ObservableTransformer<MovieDetailIntent.Initial, MovieDetailResult> { intents ->
            intents.switchMap { intent ->
                repo.getMovieDetail(MovieDetailRepoParamModel(intent.movie.movieID))
                    .toObservable()
                    .publish { publish ->
                        Observable.merge(
                            publish
                                .ofType<MovieDetailRepoOutPutModel.Error>()
                                .switchMap { error ->
                                    delayEvent(
                                        MovieDetailResult.Error(
                                            error.err.toErrorState()
                                        ),
                                        MovieDetailResult.LastStable
                                    )
                                },
                            publish
                                .ofType<MovieDetailRepoOutPutModel.Success>()
                                .map {
                                    MovieDetailResult.Detail(
                                        it.movie.title,
                                        it.movie.posterPath,
                                        getTime(it.movie.runtime),
                                        it.movie.releaseDate,
                                        it.movie.overview,
                                        it.movie.backdrops.map { back -> back.filePath }
                                    )
                                }
                        )
                    }
                    .startWith(
                        MovieDetailResult.Detail(
                            intent.movie.title,
                            intent.movie.poster,
                            "",
                            intent.movie.releasedDate,
                            intent.movie.description,
                            emptyList()
                        )
                    )
            }
        }

    private fun getTime(runtime: Int): String =
        "${runtime / 60}:${runtime % 60}"

}