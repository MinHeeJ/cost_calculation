import { useId } from "react";
import { FormField } from "../../components/FormField";
import { RequestState } from "../../components/RequestState";
import { SelectInput } from "../../components/SelectInput";
import { useCodes } from "./useCodes";

/** New selections only: the containing form owns the group, selected code and group-change reset. */
export function CodeSelect({
  group,
  value,
  onChange,
  label = "코드",
  disabled = false,
}: {
  group: string | null;
  value: string;
  onChange: (code: string) => void;
  label?: string;
  disabled?: boolean;
}) {
  const id = useId();
  const state = useCodes(group);
  return (
    <div data-testid="code-select">
      <RequestState
        loading={state.loading}
        error={state.error}
        empty={!!group && state.codes.length === 0}
        emptyReason="조건에 맞는 결과가 없습니다"
        onRetry={state.retry}
      >
        <FormField label={label} htmlFor={id}>
          <SelectInput
            id={id}
            data-testid="code-choice-input"
            value={value}
            disabled={disabled || !group}
            onChange={(event) => onChange(event.target.value)}
          >
            <option value="">코드를 선택하세요</option>
            {state.codes.map((choice) => (
              <option key={choice.code} value={choice.code}>
                {choice.name}
              </option>
            ))}
          </SelectInput>
        </FormField>
      </RequestState>
    </div>
  );
}
