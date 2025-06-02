package org.taskmangement.service;

import org.taskmangement.dto.TaskDTO;
import org.taskmangement.exception.ResourceNotFoundException;
import org.taskmangement.model.Task;
import org.taskmangement.model.User;
import org.taskmangement.enums.TaskStatus;
import org.taskmangement.repository.TaskRepository;
import org.taskmangement.repository.UserRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@Transactional
public class TaskService {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private KafkaProducerService kafkaProducerService;

    @Value("${cache.ttl}")
    private long cacheTTL;

    private static final String TASK_CACHE_KEY = "task:";
    private static final String USER_TASKS_CACHE_KEY = "user_tasks:";

    public TaskDTO createTask(TaskDTO taskDTO, Long createdById) {
        User createdBy = userRepository.findById(createdById)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + createdById));

        Task task = modelMapper.map(taskDTO, Task.class);
        task.setCreatedBy(createdBy);

        if (taskDTO.getAssignedToId() != null) {
            User assignedTo = userRepository.findById(taskDTO.getAssignedToId())
                    .orElseThrow(() -> new ResourceNotFoundException("Assigned user not found with id: " + taskDTO.getAssignedToId()));
            task.setAssignedTo(assignedTo);
        }

        Task savedTask = taskRepository.save(task);
        
        // Clear cache
        clearTaskCaches(savedTask);
        
        // Send notification
        if (savedTask.getAssignedTo() != null) {
            notificationService.createTaskAssignmentNotification(savedTask);
            kafkaProducerService.sendTaskNotification(
                "task-assignment", 
                "Task '" + savedTask.getTitle() + "' assigned to " + savedTask.getAssignedTo().getUsername()
            );
        }

        return convertToDTO(savedTask);
    }

    public TaskDTO getTaskById(Long id) {
        String cacheKey = TASK_CACHE_KEY + id;
        TaskDTO cachedTask = (TaskDTO) redisTemplate.opsForValue().get(cacheKey);
        
        if (cachedTask != null) {
            return cachedTask;
        }

        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));
        
        TaskDTO taskDTO = convertToDTO(task);
        
        // Cache the result
        redisTemplate.opsForValue().set(cacheKey, taskDTO, cacheTTL, TimeUnit.SECONDS);
        
        return taskDTO;
    }

    public Page<TaskDTO> getAllTasks(Pageable pageable) {
        Page<Task> tasks = taskRepository.findAll(pageable);
        return tasks.map(this::convertToDTO);
    }

    public Page<TaskDTO> getTasksByAssignedUser(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        
        Page<Task> tasks = taskRepository.findByAssignedTo(user, pageable);
        return tasks.map(this::convertToDTO);
    }

    public TaskDTO updateTask(Long id, TaskDTO taskDTO) {
        Task existingTask = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));

        TaskStatus oldStatus = existingTask.getStatus();
        
        // Update fields
        existingTask.setTitle(taskDTO.getTitle());
        existingTask.setDescription(taskDTO.getDescription());
        existingTask.setStatus(taskDTO.getStatus());
        existingTask.setPriority(taskDTO.getPriority());
        existingTask.setDueDate(taskDTO.getDueDate());

        if (taskDTO.getAssignedToId() != null && !taskDTO.getAssignedToId().equals(
                existingTask.getAssignedTo() != null ? existingTask.getAssignedTo().getId() : null)) {
            User assignedTo = userRepository.findById(taskDTO.getAssignedToId())
                    .orElseThrow(() -> new ResourceNotFoundException("Assigned user not found with id: " + taskDTO.getAssignedToId()));
            existingTask.setAssignedTo(assignedTo);
        }

        Task updatedTask = taskRepository.save(existingTask);
        
        // Clear cache
        clearTaskCaches(updatedTask);
        
        // Send notifications for status changes
        if (oldStatus != updatedTask.getStatus()) {
            notificationService.createTaskStatusUpdateNotification(updatedTask, oldStatus);
            kafkaProducerService.sendTaskNotification(
                "task-status-update", 
                "Task '" + updatedTask.getTitle() + "' status changed from " + oldStatus + " to " + updatedTask.getStatus()
            );
        }

        return convertToDTO(updatedTask);
    }

    public void deleteTask(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));
        
        clearTaskCaches(task);
        taskRepository.delete(task);
    }

    private TaskDTO convertToDTO(Task task) {
        TaskDTO dto = modelMapper.map(task, TaskDTO.class);
        
        if (task.getAssignedTo() != null) {
            dto.setAssignedToId(task.getAssignedTo().getId());
            dto.setAssignedToName(task.getAssignedTo().getFirstName() + " " + task.getAssignedTo().getLastName());
        }
        
        if (task.getCreatedBy() != null) {
            dto.setCreatedById(task.getCreatedBy().getId());
            dto.setCreatedByName(task.getCreatedBy().getFirstName() + " " + task.getCreatedBy().getLastName());
        }
        
        return dto;
    }

    private void clearTaskCaches(Task task) {
        // Clear specific task cache
        redisTemplate.delete(TASK_CACHE_KEY + task.getId());
        
        // Clear user tasks caches
        if (task.getAssignedTo() != null) {
            redisTemplate.delete(USER_TASKS_CACHE_KEY + task.getAssignedTo().getId());
        }
        if (task.getCreatedBy() != null) {
            redisTemplate.delete(USER_TASKS_CACHE_KEY + task.getCreatedBy().getId());
        }
    }
}