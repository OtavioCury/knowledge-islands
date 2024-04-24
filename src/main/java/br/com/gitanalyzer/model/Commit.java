package br.com.gitanalyzer.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;

import br.com.gitanalyzer.model.entity.Contributor;
import lombok.Data;

@Data
@Entity
public class Commit {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private String sha;
	private String nodeId;
	private String url;
	private String htmlUrl;
	private String commentsUrl;
	@Temporal(TemporalType.TIMESTAMP)
	private Date authorDate;
	@ManyToOne
	private Contributor author;
	@Temporal(TemporalType.TIMESTAMP)
	private Date committerDate;
	@Transient
	private Contributor committer;
	@Column(length=500)
	private String message;

	@Transient
	private List<CommitFile> commitFiles = new ArrayList<CommitFile>();

	public Commit(Contributor author, Date date, String externalId, String message) {
		super();
		this.message = message;
		this.author = author;
		this.authorDate = date;
		this.sha = externalId;
	}

	public Commit(Date date, String externalId) {
		super();
		this.authorDate = date;
		this.sha = externalId;
	}

	public Commit() {
	}

}
