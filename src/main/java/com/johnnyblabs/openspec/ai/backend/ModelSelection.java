package com.johnnyblabs.openspec.ai.backend;

import com.johnnyblabs.openspec.ai.AiApiException;
import java.util.List;
/** Pure negotiation never silently replaces an explicit manual model or reasoning effort. */
public final class ModelSelection {
 private ModelSelection() {}
 public record Selection(String wireModel, String effort, boolean fromCatalog, boolean defaultSelection) {}
 public static Selection negotiate(ModelCatalogSnapshot catalog, String modelId, String effortId, boolean requireTextInput) throws AiApiException {
  String model = modelId == null ? "" : modelId.trim(), effort = effortId == null ? "" : effortId.trim();
  if (!catalog.accountVerified()) throw new AiApiException("Current account is unverified; refresh backend status before execution.");
  if (catalog.stale()) {
   if (model.isBlank() || !effort.isBlank() || requireTextInput) throw new AiApiException("Stale model metadata cannot authorize a default model, explicit effort, or required model capability. Refresh the catalog.");
   return new Selection(model, "", false, false);
  }
  boolean defaultSelection = model.isBlank();
  List<ModelDescriptor> matches = catalog.models().stream().filter(item -> defaultSelection ? item.isDefault() : model.equals(item.id()) || model.equals(item.wireModel())).toList();
  if (matches.size() > 1) throw new AiApiException("Model catalog selection is ambiguous; select a unique model or refresh the catalog.");
  if (matches.isEmpty()) {
   if (defaultSelection || !effort.isBlank() || requireTextInput) throw new AiApiException("Selected model capabilities are unknown; refresh the catalog before choosing a default, explicit effort or required capability.");
   return new Selection(model, "", false, false);
  }
  ModelDescriptor selected = matches.getFirst();
  if (selected.wireModel().isBlank()) throw new AiApiException("Model catalog has no valid wire model identifier.");
  if (selected.textInput() == CapabilitySupport.UNSUPPORTED) throw new AiApiException("Selected model explicitly does not support text input.");
  if (requireTextInput && selected.textInput() != CapabilitySupport.SUPPORTED) throw new AiApiException("Required text input capability is unknown or unsupported for this model.");
  if (!effort.isBlank() && !selected.supportedReasoningEfforts().contains(effort)) throw new AiApiException("Selected reasoning effort is unavailable or unverified for this model.");
  return new Selection(selected.wireModel(), effort, true, defaultSelection);
 }
}
