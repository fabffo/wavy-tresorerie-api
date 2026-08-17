package fr.wavy.tresorerie.dto;

import java.util.List;

public record PageEcheancesReponse<T>(List<T> content, long totalElements, int totalPages, int number, int size) {}
