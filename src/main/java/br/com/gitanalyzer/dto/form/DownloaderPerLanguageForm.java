package br.com.gitanalyzer.dto.form;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import br.com.gitanalyzer.model.enums.LanguageEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DownloaderPerLanguageForm {

	@NotBlank
	private String path;
	@NotBlank
	private int numRepository;
	@NotNull
	private LanguageEnum language;

}
