package com.example.backend.service;

import com.example.backend.dto.CategoryRequest;
import com.example.backend.dto.CategoryResponse;
import com.example.backend.entity.Category;
import com.example.backend.exception.BusinessRuleViolationException;
import com.example.backend.exception.ConflictException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.CategoryRepository;
import com.example.backend.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CategoryServiceTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductRepository productRepository;
    @InjectMocks private CategoryService service;

    private Category a;
    private Category b;
    private Category c;

    private static Category cat(Long id, String name, Category parent) {
        Category x = new Category();
        x.setId(id);
        x.setName(name);
        x.setSlug("cat-" + id);
        x.setParent(parent);
        return x;
    }

    @BeforeEach
    void setUp() {
        a = cat(1L, "A", null);
        b = cat(2L, "B", a);
        c = cat(3L, "C", b);
        when(categoryRepository.findAll()).thenReturn(List.of(a, b, c));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(a));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(b));
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(c));
        when(categoryRepository.findByNameIgnoreCase(any(String.class))).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void category_cycle_isRejected() {
        // A -> B -> C: making C the parent of A, or A its own parent, would close a loop
        assertThrows(BusinessRuleViolationException.class, () -> service.update(1L, new CategoryRequest("A", null, 3L)));
        assertThrows(BusinessRuleViolationException.class, () -> service.update(1L, new CategoryRequest("A", null, 2L)));
        assertThrows(BusinessRuleViolationException.class, () -> service.update(1L, new CategoryRequest("A", null, 1L)));
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void category_reparentToUnrelatedOrRoot_isAllowed() {
        Category d = cat(4L, "D", null);
        when(categoryRepository.findAll()).thenReturn(List.of(a, b, c, d));
        when(categoryRepository.findById(4L)).thenReturn(Optional.of(d));

        assertEquals(a, service.update(3L, new CategoryRequest("C", null, 1L)).getParent());
        assertEquals(d, service.update(3L, new CategoryRequest("C", null, 4L)).getParent());
        assertNull(service.update(3L, new CategoryRequest("C", null, null)).getParent());
    }

    @Test
    void category_unknownParent_isNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.update(3L, new CategoryRequest("C", null, 99L)));
        assertThrows(ResourceNotFoundException.class, () -> service.create(new CategoryRequest("Mới", null, 99L)));
    }

    @Test
    void category_deleteWithProducts_isConflict() {
        when(productRepository.existsByCategoryId(3L)).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.delete(3L));
        verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    void category_deleteWithChildren_isConflict() {
        when(categoryRepository.existsByParentId(1L)).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.delete(1L));
        verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    void category_deleteEmptyLeaf_deletes() {
        service.delete(3L);
        verify(categoryRepository).delete(c);
    }

    @Test
    void category_duplicateName_isConflict_onCreateAndRename() {
        when(categoryRepository.existsByNameIgnoreCase("A")).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.create(new CategoryRequest("A", null, null)));

        when(categoryRepository.findByNameIgnoreCase("A")).thenReturn(Optional.of(a));
        assertThrows(ConflictException.class, () -> service.update(2L, new CategoryRequest("A", null, 1L)));
        // renaming to its own name is not a conflict
        assertEquals("A", service.update(1L, new CategoryRequest("A", "mô tả", null)).getName());
    }

    @Test
    void category_create_generatesTransliteratedUniqueSlug() {
        when(categoryRepository.existsBySlug("nha-cua-noi-that-xanh")).thenReturn(true);
        Category created = service.create(new CategoryRequest("Nhà Cửa & Nội Thất Xanh", null, null));
        assertEquals("nha-cua-noi-that-xanh-2", created.getSlug());
    }

    @Test
    void category_tree_buildsParentChild() {
        Category d = cat(4L, "D", null);
        List<CategoryResponse> roots = CategoryResponse.tree(List.of(a, b, c, d));

        assertEquals(2, roots.size());
        CategoryResponse ra = roots.get(0);
        assertEquals(1L, ra.id);
        assertEquals(1, ra.children.size());
        assertEquals(2L, ra.children.get(0).id);
        assertEquals(1L, ra.children.get(0).parentId);
        assertEquals(3L, ra.children.get(0).children.get(0).id);
        assertEquals(0, roots.get(1).children.size());
    }

    @Test
    void category_flatResponse_hasNoChildrenField() {
        assertNull(CategoryResponse.from(b).children);
        assertEquals(1L, CategoryResponse.from(b).parentId);
        assertEquals("cat-2", CategoryResponse.from(b).slug);
    }
}
