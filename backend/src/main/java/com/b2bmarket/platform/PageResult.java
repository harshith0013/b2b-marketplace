package com.b2bmarket.platform;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;

/**
 * One page of a list. Lists are always paged so no request can load an unbounded number of rows.
 *
 * @param page zero-based page number
 * @param hasNext whether another page follows (no total count, which would need a second query)
 */
public record PageResult<T>(List<T> items, int page, int size, boolean hasNext) {

  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 50;

  public PageResult {
    items = List.copyOf(items);
  }

  /** Validates the requested page; missing values mean the first page of the default size. */
  public static Pageable request(Integer page, Integer size, Sort sort) {
    int pageNumber = page == null ? 0 : page;
    int pageSize = size == null ? DEFAULT_SIZE : size;
    if (pageNumber < 0) {
      throw ApiException.badRequest("The page number cannot be negative.");
    }
    if (pageSize < 1 || pageSize > MAX_SIZE) {
      throw ApiException.badRequest("The page size must be between 1 and " + MAX_SIZE + ".");
    }
    return PageRequest.of(pageNumber, pageSize, sort);
  }

  public static <E, T> PageResult<T> of(Slice<E> slice, Function<? super E, T> mapper) {
    return new PageResult<>(
        slice.getContent().stream().<T>map(mapper).toList(),
        slice.getNumber(),
        slice.getSize(),
        slice.hasNext());
  }
}
