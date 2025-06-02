package org.taskmangement.repository;

import org.taskmangement.model.Task;
import org.taskmangement.model.User;
import org.taskmangement.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    
    Page<Task> findByAssignedTo(User assignedTo, Pageable pageable);
    
    Page<Task> findByCreatedBy(User createdBy, Pageable pageable);
    
    Page<Task> findByStatusAndAssignedTo(TaskStatus status, User assignedTo, Pageable pageable);
    
    List<Task> findByDueDateBeforeAndStatusNot(LocalDateTime dueDate, TaskStatus status);
    
    @Query("SELECT t FROM Task t WHERE t.assignedTo = :user AND t.status IN :statuses")
    List<Task> findTasksByUserAndStatuses(@Param("user") User user, @Param("statuses") List<TaskStatus> statuses);
    
    @Query("SELECT COUNT(t) FROM Task t WHERE t.assignedTo = :user AND t.status = :status")
    long countTasksByUserAndStatus(@Param("user") User user, @Param("status") TaskStatus status);
}