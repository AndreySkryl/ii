package org.ayfaar.app.controllers.search.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.ayfaar.app.controllers.CategoryController;
import org.ayfaar.app.controllers.NewSearchController;
import org.ayfaar.app.controllers.search.SearchResultPage;
import org.ayfaar.app.dao.CommonDao;
import org.ayfaar.app.events.SimplePushEvent;
import org.ayfaar.app.model.Category;
import org.ayfaar.app.model.Term;
import org.ayfaar.app.utils.TermService;
import org.ayfaar.app.utils.UriGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;

import static org.ayfaar.app.utils.UriGenerator.getValueFromUri;

@Component
@EnableScheduling
public class CacheUpdater {
    @Autowired
    private NewSearchController searchController;
    @Autowired
    private CategoryController categoryController;
    @Autowired
    private CommonDao commonDao;
    @Autowired
    private TermService termService;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Inject ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 604800000, initialDelay = 360000) // обновлять кеш спустя час со старта и через неделю после каждого завершения обновления
    public void update() throws IOException {
        long start = System.currentTimeMillis();

        termService.reload();
        updateCacheSearchResult();

        long end = System.currentTimeMillis();
        final String duration = DurationFormatUtils.formatDuration(end - start, "HH:mm:ss");
        eventPublisher.publishEvent(new SimplePushEvent("Кеш обновлён за "+duration));
    }

    private void updateCacheSearchResult() throws IOException {
        //clean cache for search results

        String uri = UriGenerator.generate(Term.class, "");
        List<CacheEntity> searchCacheList = commonDao.getLike(CacheEntity.class, "uri", uri + "%", Integer.MAX_VALUE);

        for (CacheEntity cache : searchCacheList) {
            final SearchResultPage searchResult = (SearchResultPage) searchController.searchWithoutCache(
                    getValueFromUri(Term.class, cache.getUri()), 0, null);
            cache.setContent(objectMapper.writeValueAsString(searchResult));
            commonDao.save(cache);
        }
    }

    public void update(String uri) {
        //clean cache by uri

        if(uri.startsWith(UriGenerator.generate(Term.class, ""))) {
            searchController.search(getValueFromUri(Term.class, uri), 0, null);
        } else if(uri.startsWith(UriGenerator.generate(Category.class, ""))) {
            categoryController.getContents(getValueFromUri(Category.class, uri));
        }
    }
}
