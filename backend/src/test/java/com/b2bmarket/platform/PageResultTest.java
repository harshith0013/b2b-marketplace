package com.b2bmarket.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;

class PageResultTest {

  @Test
  void missingValuesMeanTheFirstPageOfTheDefaultSize() {
    Pageable pageable = PageResult.request(null, null, Sort.unsorted());
    assertThat(pageable.getPageNumber()).isZero();
    assertThat(pageable.getPageSize()).isEqualTo(PageResult.DEFAULT_SIZE);
  }

  @Test
  void pagesAreBounded() {
    assertThatThrownBy(() -> PageResult.request(-1, 10, Sort.unsorted()))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> PageResult.request(0, 0, Sort.unsorted()))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> PageResult.request(0, PageResult.MAX_SIZE + 1, Sort.unsorted()))
        .isInstanceOf(ApiException.class);
    assertThat(PageResult.request(3, PageResult.MAX_SIZE, Sort.unsorted()).getPageSize())
        .isEqualTo(PageResult.MAX_SIZE);
  }

  @Test
  void mapsTheItemsAndKeepsThePagePosition() {
    var slice = new SliceImpl<>(List.of(1, 2), PageRequest.of(2, 2), true);

    PageResult<String> result = PageResult.of(slice, i -> "item " + i);

    assertThat(result).isEqualTo(new PageResult<>(List.of("item 1", "item 2"), 2, 2, true));
  }
}
