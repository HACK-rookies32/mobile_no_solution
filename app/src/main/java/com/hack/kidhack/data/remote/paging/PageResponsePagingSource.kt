package com.hack.kidhack.data.remote.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.hack.kidhack.data.remote.PageResponse

class PageResponsePagingSource<T : Any>(
    private val pageSize: Int = 20,
    private val loader: suspend (page: Int, size: Int) -> PageResponse<T>,
) : PagingSource<Int, T>() {

    override fun getRefreshKey(state: PagingState<Int, T>): Int? {
        val anchor = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchor) ?: return null
        return page.prevKey?.plus(1) ?: page.nextKey?.minus(1)
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        val page = params.key ?: 0
        return try {
            val response = loader(page, pageSize)
            val next = if (page + 1 >= response.totalPages) null else page + 1
            val prev = if (page == 0) null else page - 1
            LoadResult.Page(
                data = response.content,
                prevKey = prev,
                nextKey = next,
            )
        } catch (t: Throwable) {
            LoadResult.Error(t)
        }
    }
}
