package vn.ptit.drl.org;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    List<Organization> findByType(OrgType type);
    List<Organization> findByParentId(Long parentId);
    boolean existsByName(String name);
}
