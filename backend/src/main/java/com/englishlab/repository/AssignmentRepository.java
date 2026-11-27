package com.englishlab.repository;

import com.englishlab.model.entity.Assignment;
import com.englishlab.model.entity.Assignment.AssignmentStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    @EntityGraph(attributePaths = {"activity", "teacher"})
    Page<Assignment> findByStudentId(Long studentId, Pageable pageable);

    @EntityGraph(attributePaths = {"student", "activity"})
    Page<Assignment> findByTeacherId(Long teacherId, Pageable pageable);

    @EntityGraph(attributePaths = {"student", "activity"})
    Page<Assignment> findByTeacherIdAndStatus(Long teacherId, AssignmentStatus status, Pageable pageable);

    /** Agrega asignaciones del profesor por alumno y estado en una sola consulta (sin N+1). */
    @Query("""
            select a.student.id, a.status, count(a)
            from Assignment a
            where a.teacher.id = :teacherId and a.student.id in :studentIds
            group by a.student.id, a.status
            """)
    List<Object[]> countByTeacherAndStudentIdsGroupByStatus(
            @Param("teacherId") Long teacherId,
            @Param("studentIds") List<Long> studentIds);
}
