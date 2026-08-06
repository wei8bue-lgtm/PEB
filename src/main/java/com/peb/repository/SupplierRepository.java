package com.peb.repository;

import com.peb.model.supplier.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SupplierRepository extends JpaRepository<Supplier, String> {

    @Query("select s from Supplier s where lower(s.name) like lower(concat('%', :term, '%')) " +
           "or lower(s.city) like lower(concat('%', :term, '%')) " +
           "or lower(s.country) like lower(concat('%', :term, '%')) " +
           "or lower(s.id) like lower(concat('%', :term, '%'))")
    List<Supplier> searchByTerm(@Param("term") String term);

    /**
     * Höchste bereits vergebene laufende Nummer (numerischer Teil der ID nach
     * dem Präfix "MF"), für IDGeneratorService. Als Java-Default statt DB-spezifischer
     * substring-Query implementiert, damit es unabhängig vom DB-Dialekt funktioniert.
     * Falls MaterialRepository#findMaxSequence() bei euch anders (z.B. per @Query mit
     * nativer Funktion) implementiert ist, bitte hier angleichen.
     */
    default Long findMaxSequence() {
        return findAll().stream()
                .map(Supplier::getId)
                .filter(id -> id != null && id.length() > 2)
                .map(id -> id.replaceAll("[^0-9]", ""))
                .filter(digits -> !digits.isEmpty())
                .map(Long::parseLong)
                .max(Long::compareTo)
                .orElse(0L);
    }
}
