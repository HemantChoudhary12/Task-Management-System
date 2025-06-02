package org.taskmangement.service;

import org.taskmangement.model.Notification;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.taskmangement.enums.TaskStatus;
import org.taskmangement.model.Task;
import org.taskmangement.model.User;
import org.taskmangement.repository.NotificationRepository;
import org.taskmangement.dto.NotificationDTO;

import java.util.List;

@Service
@Transactional
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ModelMapper modelMapper;

    public void createTaskAssignmentNotification(Task task) {
        if (task.getAssignedTo() != null) {
            String message = String.format("You have been assigned a new task: %s", task.getTitle());
            Notification notification = new Notification(message, task.getAssignedTo(), task);
            notificationRepository.save(notification);
        }
    }

    public void createTaskStatusUpdateNotification(Task task, TaskStatus oldStatus) {
        if (task.getCreatedBy() != null && task.getAssignedTo() != null && 
            !task.getCreatedBy().getId().equals(task.getAssignedTo().getId())) {
            
            String message = String.format("Task '%s' status changed from %s to %s", 
                    task.getTitle(), oldStatus, task.getStatus());
            
            // Notify task creator
            Notification creatorNotification = new Notification(message, task.getCreatedBy(), task);
            notificationRepository.save(creatorNotification);
            
            // Notify assigned user if status changed to completed
            if (task.getStatus() == TaskStatus.COMPLETED) {
                String completionMessage = String.format("Task '%s' has been completed", task.getTitle());
                Notification assigneeNotification = new Notification(completionMessage, task.getAssignedTo(), task);
                notificationRepository.save(assigneeNotification);
            }
        }
    }

    public Page<NotificationDTO> getUserNotifications(User user, Pageable pageable) {
        Page<Notification> notifications = notificationRepository.findByUserOrderByCreatedAtDesc(user, pageable);
        return notifications.map(notification -> modelMapper.map(notification, NotificationDTO.class));
    }

    public List<NotificationDTO> getUnreadNotifications(User user) {
        List<Notification> notifications = notificationRepository.findByUserAndReadFalseOrderByCreatedAtDesc(user);
        return notifications.stream()
                .map(notification -> modelMapper.map(notification, NotificationDTO.class))
                .toList();
    }

    public void markNotificationAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    public long getUnreadNotificationCount(User user) {
        return notificationRepository.countByUserAndReadFalse(user);
    }
}