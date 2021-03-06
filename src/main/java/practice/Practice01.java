package practice;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.ZParams;

import java.util.*;

public class Practice01 {
    private static final int ONE_WEEK_IN_SECONDS = 7 * 86400;
    private static final int VOTE_SCORE = 432;
    private static final int ARTICLES_PER_PAGE = 25;

    public static final void main(String[] args) {
        new Practice01().run();
    }

    public void run() {
        Jedis conn = new Jedis("localhost");
        conn.auth("zhongfupeng");
        conn.select(15);

        String articleId = postArticle(
            conn, "zfp", "zfp", "http://www.baidu.com");
        System.out.println("We posted a new article with id: " + articleId);
        System.out.println("Its HASH looks like:");
        Map<String,String> articleData = conn.hgetAll("article:" + articleId);
        for (Map.Entry<String,String> entry : articleData.entrySet()){
            System.out.println("  " + entry.getKey() + ": " + entry.getValue());
        }

        System.out.println();

        articleVote(conn, "user_1", "article:" + articleId, true);
        List<Map<String,String>> articles = getArticles(conn, 1);
        printArticles(articles);
        System.out.println();

        articleVote(conn, "user_2", "article:" + articleId, false);
        articles = getArticles(conn, 1);
        printArticles(articles);
        System.out.println();

        articleVote(conn, "user_2", "article:" + articleId, true);
        articles = getArticles(conn, 1);
        printArticles(articles);
        System.out.println();
    }

    public String postArticle(Jedis conn, String user, String title, String link) {
        String articleId = String.valueOf(conn.incr("article:"));

        String voted = "voted:" + articleId;
        conn.sadd(voted, user);
        conn.expire(voted, ONE_WEEK_IN_SECONDS);

        // 练习：实现反对票的功能
        String upVoted = "voted:up:" + articleId;
        conn.sadd(upVoted, user);

        long now = System.currentTimeMillis() / 1000;
        String article = "article:" + articleId;
        HashMap<String,String> articleData = new HashMap<String,String>();
        articleData.put("title", title);
        articleData.put("link", link);
        articleData.put("user", user);
        articleData.put("now", String.valueOf(now));
        articleData.put("votes", "1");
        conn.hmset(article, articleData);
        conn.zadd("score:", now + VOTE_SCORE, article);
        conn.zadd("time:", now, article);

        return articleId;
    }

    public void articleVote(Jedis conn, String user, String article, Boolean isUp) {
        long cutoff = (System.currentTimeMillis() / 1000) - ONE_WEEK_IN_SECONDS;
        if (conn.zscore("time:", article) < cutoff){
            return;
        }

        // 练习：实现反对票的功能
        String up = "up:";
        String down = "down:";
        int score = VOTE_SCORE;

        if (!isUp) {
            String tmp = up;
            up = down;
            down = tmp;
            score = -VOTE_SCORE;
        }

        String articleId = article.substring(article.indexOf(':') + 1);

        if (conn.sadd("voted:" + articleId, user) == 1) {
            conn.zincrby("score:", score, article);
            conn.sadd("voted:" + up + articleId, user);
            conn.hincrBy(article, "votes", 1);
        } else if (!conn.sismember("voted:" + up + articleId, user)) {
            conn.smove("voted:" + down + articleId, "voted:" + up + articleId, user);
            conn.zincrby("score:", score, article);
        }
    }


    public List<Map<String,String>> getArticles(Jedis conn, int page) {
        return getArticles(conn, page, "score:");
    }

    public List<Map<String,String>> getArticles(Jedis conn, int page, String order) {
        int start = (page - 1) * ARTICLES_PER_PAGE;
        int end = start + ARTICLES_PER_PAGE - 1;

        Set<String> ids = conn.zrevrange(order, start, end);
        List<Map<String,String>> articles = new ArrayList<Map<String,String>>();
        for (String id : ids){
            Map<String,String> articleData = conn.hgetAll(id);
            articleData.put("id", id);
            articles.add(articleData);
        }

        return articles;
    }

    public void addGroups(Jedis conn, String articleId, String[] toAdd) {
        String article = "article:" + articleId;
        for (String group : toAdd) {
            conn.sadd("group:" + group, article);
        }
    }

    public List<Map<String,String>> getGroupArticles(Jedis conn, String group, int page) {
        return getGroupArticles(conn, group, page, "score:");
    }

    public List<Map<String,String>> getGroupArticles(Jedis conn, String group, int page, String order) {
        String key = order + group;
        if (!conn.exists(key)) {
            ZParams params = new ZParams().aggregate(ZParams.Aggregate.MAX);
            conn.zinterstore(key, params, "group:" + group, order);
            conn.expire(key, 60);
        }
        return getArticles(conn, page, key);
    }

    private void printArticles(List<Map<String,String>> articles){
        for (Map<String,String> article : articles){
            System.out.println("  id: " + article.get("id"));
            for (Map.Entry<String,String> entry : article.entrySet()){
                if (entry.getKey().equals("id")){
                    continue;
                }
                System.out.println("    " + entry.getKey() + ": " + entry.getValue());
            }
        }
    }
}
