package com.smarttest.manager.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ManagerTaskDao {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void insertManagerTask(String managerTaskId, String storyid, String phase, String doctype,
                                  String envDto, String callbackUrl) {
        String sql = "INSERT INTO manager_tasks (manager_task_id, storyid, phase, doctype, env_dto, callback_url, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'pending')";
        jdbcTemplate.update(sql, managerTaskId, storyid, phase, doctype, envDto, callbackUrl);
    }

    public void updateManagerTaskStatus(String managerTaskId, String status) {
        String sql = "UPDATE manager_tasks SET status = ? WHERE manager_task_id = ?";
        jdbcTemplate.update(sql, status, managerTaskId);
    }

    @Transactional
    public void insertTaskDetail(String managerTaskId, String downstreamTaskId, String testPointId) {
        String sql = "INSERT INTO manager_task_details (manager_task_id, downstream_task_id, test_point_id, status) " +
                "VALUES (?, ?, ?, 'pending')";
        jdbcTemplate.update(sql, managerTaskId, downstreamTaskId, testPointId);
    }

    public void updateTaskDetailStatus(String managerTaskId, String downstreamTaskId, String status) {
        String sql = "UPDATE manager_task_details SET status = ? WHERE manager_task_id = ? AND downstream_task_id = ?";
        jdbcTemplate.update(sql, status, managerTaskId, downstreamTaskId);
    }

    public List<String> queryDownstreamTaskIds(String managerTaskId) {
        String sql = "SELECT downstream_task_id FROM manager_task_details WHERE manager_task_id = ? ORDER BY id";
        return jdbcTemplate.queryForList(sql, String.class, managerTaskId);
    }

    public Map<String, String> queryTaskDetailMap(String managerTaskId) {
        String sql = "SELECT downstream_task_id, test_point_id FROM manager_task_details WHERE manager_task_id = ? ORDER BY id";
        return jdbcTemplate.query(sql, this::extractTaskDetailMap, managerTaskId);
    }

    private Map<String, String> extractTaskDetailMap(ResultSet rs) throws SQLException {
        Map<String, String> map = new LinkedHashMap<>();
        while (rs.next()) {
            map.put(rs.getString("downstream_task_id"), rs.getString("test_point_id"));
        }
        return map;
    }

    public String queryCallbackUrl(String managerTaskId) {
        String sql = "SELECT callback_url FROM manager_tasks WHERE manager_task_id = ?";
        List<String> list = jdbcTemplate.queryForList(sql, String.class, managerTaskId);
        return list.isEmpty() ? null : list.get(0);
    }

    public String queryStoryid(String managerTaskId) {
        String sql = "SELECT storyid FROM manager_tasks WHERE manager_task_id = ?";
        List<String> list = jdbcTemplate.queryForList(sql, String.class, managerTaskId);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<String> queryRunningManagerTaskIds() {
        String sql = "SELECT manager_task_id FROM manager_tasks WHERE status = 'running'";
        return jdbcTemplate.queryForList(sql, String.class);
    }
}
